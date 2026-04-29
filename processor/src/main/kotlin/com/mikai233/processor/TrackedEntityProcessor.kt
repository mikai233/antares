package com.mikai233.processor

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

private const val TRACK_ENTITY_ANNOTATION = "com.mikai233.common.db.tracked.TrackEntity"
private const val ENTITY_QUALIFIED_NAME = "com.mikai233.common.db.Entity"

class TrackedEntityProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {
    private val processed = mutableSetOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation(TRACK_ENTITY_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .forEach { declaration ->
                val qualifiedName = declaration.qualifiedName?.asString() ?: return@forEach
                if (!processed.add(qualifiedName)) {
                    return@forEach
                }
                generateTrackedEntity(declaration)
            }
        return emptyList()
    }

    private fun generateTrackedEntity(declaration: KSClassDeclaration) {
        if (!declaration.isSubclassOf(ENTITY_QUALIFIED_NAME)) {
            environment.logger.error("@TrackEntity class must implement Entity", declaration)
            return
        }
        val sourceFile = declaration.containingFile
        if (sourceFile == null) {
            environment.logger.error("@TrackEntity class must have a source file", declaration)
            return
        }

        val packageName = "${declaration.packageName.asString()}.tracked"
        val nestedObjects = linkedMapOf<String, NestedTrackedObject>()
        val properties = declaration.getDeclaredProperties()
            .filterNot { it.hasAnnotation("Transient") || it.hasAnnotation("TrackIgnore") }
            .map { property -> property.toTrackedProperty(packageName, nestedObjects) }
            .toList()
        val unsupportedProperties = properties.asSequence()
            .plus(nestedObjects.values.asSequence().flatMap { it.properties.asSequence() })
            .filter { it.kind == PropertyKind.UNSUPPORTED }
            .toList()
        if (unsupportedProperties.isNotEmpty()) {
            unsupportedProperties.forEach { property ->
                environment.logger.error(
                    "Unsupported tracked property ${property.source.parentDeclaration?.qualifiedName?.asString()}.${property.name}: " +
                            property.type.resolve().declaration.qualifiedName?.asString(),
                    property.type,
                )
            }
            return
        }

        val idProperty = properties.find { it.source.hasAnnotation("Id") || it.source.hasAnnotation("StableId") }
        if (idProperty == null) {
            environment.logger.error("@TrackEntity class must have an @Id or @StableId property", declaration)
            return
        }

        val sourceClassName = declaration.toClassName()
        val trackedClassName = ClassName(packageName, "${declaration.simpleName.asString()}Tracked")
        val factoryName = "${declaration.simpleName.asString()}TrackedFactory"

        val trackedType = TypeSpec.classBuilder(trackedClassName)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("ctx", TRACK_CONTEXT)
                    .addParameter("source", sourceClassName)
                    .build(),
            )
            .addSuperinterface(TRACKED_ENTITY.parameterizedBy(sourceClassName))
            .addProperty(
                PropertySpec.builder("context", TRACK_CONTEXT)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("ctx")
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("trackId", ANY.copy(nullable = true))
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer("source.%N", idProperty.name)
                    .build(),
            )
            .addProperties(properties.flatMap { it.toPropertySpecs(OwnerKind.ENTITY) })
            .addFunction(properties.toPersistentValueFunction(idProperty.name))
            .addFunction(declaration.toEntityFunction(properties, sourceClassName))
            .build()

        val factoryType = TypeSpec.objectBuilder(factoryName)
            .addFunction(
                FunSpec.builder("wrap")
                    .addParameter("slot", String::class)
                    .addParameter("bucket", INT)
                    .addParameter("entity", sourceClassName)
                    .addParameter("queue", PENDING_WRITE_QUEUE)
                    .addParameter(
                        ParameterSpec.builder("fieldRoot", String::class)
                            .defaultValue("%S", "")
                            .build(),
                    )
                    .returns(trackedClassName)
                    .addStatement(
                        "return %T(%T(slot, bucket, entity.%N, queue, fieldRoot), entity)",
                        trackedClassName,
                        TRACK_CONTEXT,
                        idProperty.name,
                    )
                    .build(),
            )
            .build()

        val fileSpec = FileSpec.builder(packageName, trackedClassName.simpleName)
            .apply {
                nestedObjects.values.forEach { nestedObject ->
                    addType(nestedObject.toTypeSpec())
                }
            }
            .addType(trackedType)
            .addType(factoryType)
            .build()

        val dependencyFiles = (listOf(sourceFile) + nestedObjects.values.mapNotNull { it.declaration.containingFile })
            .distinct()
        environment.codeGenerator.createNewFile(
            Dependencies(false, *dependencyFiles.toTypedArray()),
            packageName,
            trackedClassName.simpleName,
        ).use { output ->
            output.writer().use { writer -> fileSpec.writeTo(writer) }
        }
    }

    private fun KSPropertyDeclaration.toTrackedProperty(
        packageName: String,
        nestedObjects: MutableMap<String, NestedTrackedObject>,
    ): TrackedProperty {
        val type = type.resolve()
        val kind = type.propertyKind(isMutable)
        val nestedClassName = if (kind == PropertyKind.NESTED_OBJECT) {
            registerNestedTrackedObject(type.declaration as KSClassDeclaration, packageName, nestedObjects)
        } else {
            null
        }
        val typeName = kind.generatedTypeName(type, nestedClassName)
        return TrackedProperty(
            source = this,
            name = simpleName.asString(),
            type = this.type,
            typeName = typeName,
            kind = kind,
            mutable = isMutable,
            nestedClassName = nestedClassName,
        )
    }

    private fun PropertyKind.generatedTypeName(type: KSType, nestedClassName: ClassName?): TypeName {
        val typeArguments = type.arguments.mapNotNull { it.type?.toTypeName() }
        return when (this) {
            PropertyKind.MAP -> MUTABLE_MAP.parameterizedBy(typeArguments)
            PropertyKind.LIST -> MUTABLE_LIST.parameterizedBy(typeArguments)
            PropertyKind.SET -> MUTABLE_SET.parameterizedBy(typeArguments)
            PropertyKind.DEQUE -> DEQUE.parameterizedBy(typeArguments)
            PropertyKind.NULLABLE_MAP -> MUTABLE_MAP.parameterizedBy(typeArguments).copy(nullable = true)
            PropertyKind.NULLABLE_LIST -> MUTABLE_LIST.parameterizedBy(typeArguments).copy(nullable = true)
            PropertyKind.NULLABLE_SET -> MUTABLE_SET.parameterizedBy(typeArguments).copy(nullable = true)
            PropertyKind.NULLABLE_DEQUE -> DEQUE.parameterizedBy(typeArguments).copy(nullable = true)
            PropertyKind.INT_ARRAY -> TRACKED_INT_ARRAY
            PropertyKind.LONG_ARRAY -> TRACKED_LONG_ARRAY
            PropertyKind.BOOLEAN_ARRAY -> TRACKED_BOOLEAN_ARRAY
            PropertyKind.DOUBLE_ARRAY -> TRACKED_DOUBLE_ARRAY
            PropertyKind.FLOAT_ARRAY -> TRACKED_FLOAT_ARRAY
            PropertyKind.NESTED_OBJECT -> requireNotNull(nestedClassName)
            else -> type.toTypeName()
        }
    }

    private fun KSType.propertyKind(mutable: Boolean): PropertyKind {
        val declaration = declaration as? KSClassDeclaration ?: return PropertyKind.UNSUPPORTED
        val qualifiedName = declaration.qualifiedName?.asString()
        if (nullability == Nullability.NULLABLE && !isScalar()) {
            if (!mutable) {
                return PropertyKind.UNSUPPORTED
            }
            return when {
                declaration.isTypeOrSubclassOf("kotlin.collections.MutableMap") -> PropertyKind.NULLABLE_MAP
                declaration.isTypeOrSubclassOf("kotlin.collections.MutableList") -> PropertyKind.NULLABLE_LIST
                declaration.isTypeOrSubclassOf("kotlin.collections.MutableSet") -> PropertyKind.NULLABLE_SET
                declaration.isTypeOrSubclassOf("java.util.Deque") -> PropertyKind.NULLABLE_DEQUE
                else -> PropertyKind.UNSUPPORTED
            }
        }
        return when {
            isScalar() -> PropertyKind.SCALAR
            qualifiedName == "kotlin.IntArray" -> PropertyKind.INT_ARRAY
            qualifiedName == "kotlin.LongArray" -> PropertyKind.LONG_ARRAY
            qualifiedName == "kotlin.BooleanArray" -> PropertyKind.BOOLEAN_ARRAY
            qualifiedName == "kotlin.DoubleArray" -> PropertyKind.DOUBLE_ARRAY
            qualifiedName == "kotlin.FloatArray" -> PropertyKind.FLOAT_ARRAY
            declaration.isTypeOrSubclassOf("kotlin.collections.MutableMap") -> PropertyKind.MAP
            declaration.isTypeOrSubclassOf("kotlin.collections.MutableList") -> PropertyKind.LIST
            declaration.isTypeOrSubclassOf("kotlin.collections.MutableSet") -> PropertyKind.SET
            declaration.isTypeOrSubclassOf("java.util.Deque") -> PropertyKind.DEQUE
            declaration.isTrackableObject() && !mutable -> PropertyKind.NESTED_OBJECT
            else -> PropertyKind.UNSUPPORTED
        }
    }

    private fun KSType.isScalar(): Boolean {
        val declaration = declaration as? KSClassDeclaration ?: return false
        val qualifiedName = declaration.qualifiedName?.asString()
        return qualifiedName in SCALAR_QUALIFIED_NAMES || declaration.isSubclassOf("kotlin.Enum")
    }

    private fun KSClassDeclaration.isTrackableObject(): Boolean {
        return classKind == ClassKind.CLASS &&
                primaryConstructor != null &&
                !isSubclassOf(ENTITY_QUALIFIED_NAME) &&
                !isSubclassOf("kotlin.Enum") &&
                qualifiedName?.asString()?.startsWith("kotlin.") != true
    }

    private fun registerNestedTrackedObject(
        declaration: KSClassDeclaration,
        packageName: String,
        nestedObjects: MutableMap<String, NestedTrackedObject>,
    ): ClassName {
        val qualifiedName = declaration.qualifiedName?.asString()
            ?: error("Nested tracked object must have a qualified name")
        nestedObjects[qualifiedName]?.let { return it.className }

        val className = ClassName(packageName, "${declaration.simpleName.asString()}TrackedValue")
        nestedObjects[qualifiedName] = NestedTrackedObject(declaration, className, emptyList())
        val properties = declaration.getDeclaredProperties()
            .filterNot { it.hasAnnotation("Transient") || it.hasAnnotation("TrackIgnore") }
            .map { property -> property.toTrackedProperty(packageName, nestedObjects) }
            .toList()
        nestedObjects[qualifiedName] = NestedTrackedObject(declaration, className, properties)
        return className
    }

    private fun KSPropertyDeclaration.hasAnnotation(shortName: String): Boolean {
        return annotations.any { it.shortName.asString() == shortName }
    }
}

private fun KSClassDeclaration.isTypeOrSubclassOf(qualifiedName: String): Boolean {
    return this.qualifiedName?.asString() == qualifiedName || isSubclassOf(qualifiedName)
}

private data class TrackedProperty(
    val source: KSPropertyDeclaration,
    val name: String,
    val type: KSTypeReference,
    val typeName: TypeName,
    val kind: PropertyKind,
    val mutable: Boolean,
    val nestedClassName: ClassName?,
)

private data class NestedTrackedObject(
    val declaration: KSClassDeclaration,
    val className: ClassName,
    val properties: List<TrackedProperty>,
)

private enum class PropertyKind {
    SCALAR,
    MAP,
    LIST,
    SET,
    DEQUE,
    INT_ARRAY,
    LONG_ARRAY,
    BOOLEAN_ARRAY,
    DOUBLE_ARRAY,
    FLOAT_ARRAY,
    NESTED_OBJECT,
    NULLABLE_MAP,
    NULLABLE_LIST,
    NULLABLE_SET,
    NULLABLE_DEQUE,
    UNSUPPORTED,
}

private enum class OwnerKind {
    ENTITY,
    OBJECT,
}

private fun NestedTrackedObject.toTypeSpec(): TypeSpec {
    val sourceClassName = declaration.toClassName()
    return TypeSpec.classBuilder(className)
        .primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter("path", DB_PATH)
                .addParameter("queue", CHANGE_QUEUE)
                .addParameter("source", sourceClassName)
                .build(),
        )
        .superclass(TRACKED_OBJECT_SUPPORT)
        .addSuperclassConstructorParameter("queue")
        .addProperties(properties.flatMap { it.toPropertySpecs(OwnerKind.OBJECT) })
        .addFunction(properties.toPersistentValueFunction())
        .addFunction(declaration.toValueFunction(properties, sourceClassName))
        .build()
}

private fun TrackedProperty.toPropertySpecs(owner: OwnerKind): List<PropertySpec> {
    return when (kind) {
        PropertyKind.NULLABLE_MAP,
        PropertyKind.NULLABLE_LIST,
        PropertyKind.NULLABLE_SET,
        PropertyKind.NULLABLE_DEQUE,
            -> nullableCollectionPropertySpecs(owner)

        else -> listOf(toPropertySpec(owner))
    }
}

private fun TrackedProperty.toPropertySpec(owner: OwnerKind): PropertySpec {
    val builder = PropertySpec.builder(name, typeName)
    return when (kind) {
        PropertyKind.SCALAR -> {
            if (mutable) {
                builder.mutable(true)
                    .delegate(
                        CodeBlock.of(
                            "%M(%L, source.%N, %L%L)",
                            TRACKED_VALUE,
                            pathCode(owner),
                            name,
                            queueCode(owner),
                            dirtyTargetProviderCode(owner),
                        ),
                    )
                    .build()
            } else {
                builder.initializer("source.%N", name).build()
            }
        }

        PropertyKind.MAP -> builder
            .delegate(
                CodeBlock.of(
                    "%M(%L, source.%N, %L%L)",
                    TRACKED_MAP,
                    pathCode(owner),
                    name,
                    queueCode(owner),
                    dirtyTargetProviderCode(owner, named = true),
                ),
            )
            .build()

        PropertyKind.LIST -> builder
            .delegate(
                CodeBlock.of(
                    "%M(%L, source.%N, %L%L)",
                    TRACKED_LIST,
                    pathCode(owner),
                    name,
                    queueCode(owner),
                    dirtyTargetProviderCode(owner, named = true),
                ),
            )
            .build()

        PropertyKind.SET -> builder
            .delegate(
                CodeBlock.of(
                    "%M(%L, source.%N, %L%L)",
                    TRACKED_SET,
                    pathCode(owner),
                    name,
                    queueCode(owner),
                    dirtyTargetProviderCode(owner, named = true),
                ),
            )
            .build()

        PropertyKind.DEQUE -> builder
            .delegate(
                CodeBlock.of(
                    "%M(%L, source.%N, %L%L)",
                    TRACKED_DEQUE,
                    pathCode(owner),
                    name,
                    queueCode(owner),
                    dirtyTargetProviderCode(owner, named = true),
                ),
            )
            .build()

        PropertyKind.INT_ARRAY -> builder
            .delegate(
                CodeBlock.of(
                    "%M(%L, source.%N, %L)",
                    TRACKED_INT_ARRAY_FUN,
                    pathCode(owner),
                    name,
                    queueCode(owner),
                ),
            )
            .build()

        PropertyKind.LONG_ARRAY -> builder
            .delegate(
                CodeBlock.of(
                    "%M(%L, source.%N, %L)",
                    TRACKED_LONG_ARRAY_FUN,
                    pathCode(owner),
                    name,
                    queueCode(owner),
                ),
            )
            .build()

        PropertyKind.BOOLEAN_ARRAY -> builder
            .delegate(
                CodeBlock.of(
                    "%M(%L, source.%N, %L)",
                    TRACKED_BOOLEAN_ARRAY_FUN,
                    pathCode(owner),
                    name,
                    queueCode(owner),
                ),
            )
            .build()

        PropertyKind.DOUBLE_ARRAY -> builder
            .delegate(
                CodeBlock.of(
                    "%M(%L, source.%N, %L)",
                    TRACKED_DOUBLE_ARRAY_FUN,
                    pathCode(owner),
                    name,
                    queueCode(owner),
                ),
            )
            .build()

        PropertyKind.FLOAT_ARRAY -> builder
            .delegate(
                CodeBlock.of(
                    "%M(%L, source.%N, %L)",
                    TRACKED_FLOAT_ARRAY_FUN,
                    pathCode(owner),
                    name,
                    queueCode(owner),
                ),
            )
            .build()

        PropertyKind.NESTED_OBJECT -> builder
            .initializer(
                "%T(%L, %L, source.%N)",
                requireNotNull(nestedClassName),
                pathCode(owner),
                queueCode(owner),
                name,
            )
            .build()

        PropertyKind.NULLABLE_MAP,
        PropertyKind.NULLABLE_LIST,
        PropertyKind.NULLABLE_SET,
        PropertyKind.NULLABLE_DEQUE,
            -> error("Nullable collection properties must be generated with a backing field")

        PropertyKind.UNSUPPORTED -> error("Unsupported property kind")
    }
}

private fun TrackedProperty.nullableCollectionPropertySpecs(owner: OwnerKind): List<PropertySpec> {
    val backingName = "${name}Value"
    val trackedCollectionClass = when (kind) {
        PropertyKind.NULLABLE_MAP -> TRACKED_MUTABLE_MAP
        PropertyKind.NULLABLE_LIST -> TRACKED_MUTABLE_LIST
        PropertyKind.NULLABLE_SET -> TRACKED_MUTABLE_SET
        PropertyKind.NULLABLE_DEQUE -> TRACKED_MUTABLE_DEQUE
        else -> error("Expected nullable collection kind")
    }
    val backing = PropertySpec.builder(backingName, typeName)
        .addModifiers(KModifier.PRIVATE)
        .mutable(true)
        .initializer(
            "source.%N?.let { %T(%L, it, %L%L) }",
            name,
            trackedCollectionClass,
            pathCode(owner),
            queueCode(owner),
            dirtyTargetProviderCode(owner, named = true),
        )
        .build()
    val property = PropertySpec.builder(name, typeName)
        .mutable(true)
        .getter(
            FunSpec.getterBuilder()
                .addStatement("return %N", backingName)
                .build(),
        )
        .setter(
            FunSpec.setterBuilder()
                .addParameter("value", typeName)
                .addCode(
                    buildCodeBlock {
                        add(
                            "val valueToStore = value?.let { %T(%L, it, %L%L) }\n",
                            trackedCollectionClass,
                            pathCode(owner),
                            queueCode(owner),
                            dirtyTargetProviderCode(owner, named = true),
                        )
                        add(
                            "if (%M(%N) == %M(valueToStore)) return\n",
                            PERSISTENT_VALUE_OF,
                            backingName,
                            PERSISTENT_VALUE_OF,
                        )
                        add("%N = valueToStore\n", backingName)
                        when (owner) {
                            OwnerKind.ENTITY -> add(
                                "%L.enqueue(%T.Set(%L, valueToStore))\n",
                                queueCode(owner),
                                CHANGE_OP,
                                pathCode(owner),
                            )

                            OwnerKind.OBJECT -> add("markSet(%L, valueToStore)\n", pathCode(owner))
                        }
                    },
                )
                .build(),
        )
        .build()
    return listOf(backing, property)
}

private fun TrackedProperty.pathCode(owner: OwnerKind): CodeBlock {
    return when (owner) {
        OwnerKind.ENTITY -> CodeBlock.of("context.path(%S)", name)
        OwnerKind.OBJECT -> CodeBlock.of("path.child(%S)", name)
    }
}

private fun queueCode(owner: OwnerKind): CodeBlock {
    return when (owner) {
        OwnerKind.ENTITY -> CodeBlock.of("context.queue")
        OwnerKind.OBJECT -> CodeBlock.of("queue")
    }
}

private fun dirtyTargetProviderCode(owner: OwnerKind, named: Boolean = false): CodeBlock {
    if (owner != OwnerKind.OBJECT) {
        return CodeBlock.of("")
    }
    return if (named) {
        CodeBlock.of(", dirtyTargetProvider = { currentDirtyTarget() }")
    } else {
        CodeBlock.of(", { currentDirtyTarget() }")
    }
}

private fun List<TrackedProperty>.toPersistentValueFunction(idPropertyName: String? = null): FunSpec {
    return FunSpec.builder("toPersistentValue")
        .addModifiers(KModifier.OVERRIDE)
        .returns(ANY.copy(nullable = true))
        .addCode(
            buildCodeBlock {
                add("return mapOf(\n")
                forEach { property ->
                    val persistentName = if (property.name == idPropertyName) "_id" else property.name
                    add("%S to %M(%N),\n", persistentName, PERSISTENT_VALUE_OF, property.name)
                }
                add(")\n")
            },
        )
        .build()
}

private fun KSClassDeclaration.toEntityFunction(
    properties: List<TrackedProperty>,
    sourceClassName: ClassName,
): FunSpec {
    return toSourceFunction("toEntity", properties, sourceClassName, KModifier.OVERRIDE)
}

private fun KSClassDeclaration.toValueFunction(
    properties: List<TrackedProperty>,
    sourceClassName: ClassName,
): FunSpec {
    return toSourceFunction("toValue", properties, sourceClassName)
}

private fun KSClassDeclaration.toSourceFunction(
    name: String,
    properties: List<TrackedProperty>,
    sourceClassName: ClassName,
    vararg modifiers: KModifier,
): FunSpec {
    val propertyByName = properties.associateBy { it.name }
    val constructor = primaryConstructor
    val constructorParameters = constructor?.parameters.orEmpty()
    return FunSpec.builder(name)
        .addModifiers(*modifiers)
        .returns(sourceClassName)
        .addCode(
            buildCodeBlock {
                add("return %T(\n", sourceClassName)
                constructorParameters.forEach { parameter ->
                    val name = parameter.name?.asString() ?: return@forEach
                    val property = propertyByName.getValue(name)
                    add("%N = ", name)
                    add(property.toEntityArgument())
                    add(",\n")
                }
                add(")\n")
            },
        )
        .build()
}

private fun TrackedProperty.toEntityArgument(): CodeBlock {
    return when (kind) {
        PropertyKind.INT_ARRAY -> CodeBlock.of("%N.toIntArray()", name)
        PropertyKind.LONG_ARRAY -> CodeBlock.of("%N.toLongArray()", name)
        PropertyKind.BOOLEAN_ARRAY -> CodeBlock.of("%N.toBooleanArray()", name)
        PropertyKind.DOUBLE_ARRAY -> CodeBlock.of("%N.toDoubleArray()", name)
        PropertyKind.FLOAT_ARRAY -> CodeBlock.of("%N.toFloatArray()", name)
        PropertyKind.NESTED_OBJECT -> CodeBlock.of("%N.toValue()", name)
        else -> CodeBlock.of("%N", name)
    }
}

private fun buildCodeBlock(block: CodeBlock.Builder.() -> Unit): CodeBlock {
    return CodeBlock.builder().apply(block).build()
}

private val SCALAR_QUALIFIED_NAMES = setOf(
    "kotlin.String",
    "kotlin.Boolean",
    "kotlin.Byte",
    "kotlin.Short",
    "kotlin.Int",
    "kotlin.Long",
    "kotlin.Float",
    "kotlin.Double",
    "kotlin.Char",
    "kotlin.UByte",
    "kotlin.UShort",
    "kotlin.UInt",
    "kotlin.ULong",
)

private val TRACK_CONTEXT = ClassName("com.mikai233.common.db.tracked", "TrackContext")
private val TRACKED_ENTITY = ClassName("com.mikai233.common.db.tracked", "TrackedEntity")
private val TRACKED_OBJECT_SUPPORT = ClassName("com.mikai233.common.db.tracked", "TrackedObjectSupport")
private val PENDING_WRITE_QUEUE = ClassName("com.mikai233.common.db.tracked", "PendingWriteQueue")
private val CHANGE_QUEUE = ClassName("com.mikai233.common.db.tracked", "ChangeQueue")
private val CHANGE_OP = ClassName("com.mikai233.common.db.tracked", "ChangeOp")
private val DB_PATH = ClassName("com.mikai233.common.db.tracked", "DbPath")
private val TRACKED_INT_ARRAY = ClassName("com.mikai233.common.db.tracked", "TrackedIntArray")
private val TRACKED_LONG_ARRAY = ClassName("com.mikai233.common.db.tracked", "TrackedLongArray")
private val TRACKED_BOOLEAN_ARRAY = ClassName("com.mikai233.common.db.tracked", "TrackedBooleanArray")
private val TRACKED_DOUBLE_ARRAY = ClassName("com.mikai233.common.db.tracked", "TrackedDoubleArray")
private val TRACKED_FLOAT_ARRAY = ClassName("com.mikai233.common.db.tracked", "TrackedFloatArray")
private val MUTABLE_MAP = ClassName("kotlin.collections", "MutableMap")
private val MUTABLE_LIST = ClassName("kotlin.collections", "MutableList")
private val MUTABLE_SET = ClassName("kotlin.collections", "MutableSet")
private val DEQUE = ClassName("java.util", "Deque")
private val TRACKED_MUTABLE_MAP = ClassName("com.mikai233.common.db.tracked", "TrackedMutableMap")
private val TRACKED_MUTABLE_LIST = ClassName("com.mikai233.common.db.tracked", "TrackedMutableList")
private val TRACKED_MUTABLE_SET = ClassName("com.mikai233.common.db.tracked", "TrackedMutableSet")
private val TRACKED_MUTABLE_DEQUE = ClassName("com.mikai233.common.db.tracked", "TrackedMutableDeque")

private val TRACKED_VALUE = MemberName("com.mikai233.common.db.tracked", "trackedValue")
private val TRACKED_MAP = MemberName("com.mikai233.common.db.tracked", "trackedMap")
private val TRACKED_LIST = MemberName("com.mikai233.common.db.tracked", "trackedList")
private val TRACKED_SET = MemberName("com.mikai233.common.db.tracked", "trackedSet")
private val TRACKED_DEQUE = MemberName("com.mikai233.common.db.tracked", "trackedDeque")
private val TRACKED_INT_ARRAY_FUN = MemberName("com.mikai233.common.db.tracked", "trackedIntArray")
private val TRACKED_LONG_ARRAY_FUN = MemberName("com.mikai233.common.db.tracked", "trackedLongArray")
private val TRACKED_BOOLEAN_ARRAY_FUN = MemberName("com.mikai233.common.db.tracked", "trackedBooleanArray")
private val TRACKED_DOUBLE_ARRAY_FUN = MemberName("com.mikai233.common.db.tracked", "trackedDoubleArray")
private val TRACKED_FLOAT_ARRAY_FUN = MemberName("com.mikai233.common.db.tracked", "trackedFloatArray")
private val PERSISTENT_VALUE_OF = MemberName("com.mikai233.common.db.tracked", "persistentValueOf")
