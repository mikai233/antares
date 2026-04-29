package com.mikai233.processor

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

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
        val properties = declaration.getDeclaredProperties()
            .filterNot { it.hasAnnotation("Transient") || it.hasAnnotation("TrackIgnore") }
            .map { property -> property.toTrackedProperty() }
            .toList()
        if (properties.any { it.kind == PropertyKind.UNSUPPORTED }) {
            properties.filter { it.kind == PropertyKind.UNSUPPORTED }.forEach { property ->
                environment.logger.error(
                    "Unsupported tracked property ${declaration.qualifiedName?.asString()}.${property.name}: " +
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

        val packageName = "${declaration.packageName.asString()}.tracked"
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
                PropertySpec.builder("trackId", ANY.copy(nullable = true))
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer("source.%N", idProperty.name)
                    .build(),
            )
            .addProperties(properties.map { it.toPropertySpec() })
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
            .addType(trackedType)
            .addType(factoryType)
            .build()

        environment.codeGenerator.createNewFile(
            Dependencies(false, sourceFile),
            packageName,
            trackedClassName.simpleName,
        ).use { output ->
            output.writer().use { writer -> fileSpec.writeTo(writer) }
        }
    }

    private fun KSPropertyDeclaration.toTrackedProperty(): TrackedProperty {
        val type = type.resolve()
        val kind = type.propertyKind()
        val typeName = kind.generatedTypeName(type)
        return TrackedProperty(
            source = this,
            name = simpleName.asString(),
            type = this.type,
            typeName = typeName,
            kind = kind,
            mutable = isMutable,
        )
    }

    private fun KSTypePropertyKind.generatedTypeName(type: com.google.devtools.ksp.symbol.KSType): TypeName {
        return when (this) {
            PropertyKind.MAP -> MUTABLE_MAP.parameterizedBy(type.arguments.mapNotNull { it.type?.toTypeName() })
            PropertyKind.LIST -> MUTABLE_LIST.parameterizedBy(type.arguments.mapNotNull { it.type?.toTypeName() })
            PropertyKind.SET -> MUTABLE_SET.parameterizedBy(type.arguments.mapNotNull { it.type?.toTypeName() })
            PropertyKind.DEQUE -> DEQUE.parameterizedBy(type.arguments.mapNotNull { it.type?.toTypeName() })
            PropertyKind.INT_ARRAY -> TRACKED_INT_ARRAY
            PropertyKind.LONG_ARRAY -> TRACKED_LONG_ARRAY
            PropertyKind.BOOLEAN_ARRAY -> TRACKED_BOOLEAN_ARRAY
            PropertyKind.DOUBLE_ARRAY -> TRACKED_DOUBLE_ARRAY
            PropertyKind.FLOAT_ARRAY -> TRACKED_FLOAT_ARRAY
            else -> type.toTypeName()
        }
    }

    private fun com.google.devtools.ksp.symbol.KSType.propertyKind(): PropertyKind {
        if (nullability == Nullability.NULLABLE && !isScalar()) {
            return PropertyKind.UNSUPPORTED
        }
        val declaration = declaration as? KSClassDeclaration ?: return PropertyKind.UNSUPPORTED
        val qualifiedName = declaration.qualifiedName?.asString()
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
            else -> PropertyKind.UNSUPPORTED
        }
    }

    private fun com.google.devtools.ksp.symbol.KSType.isScalar(): Boolean {
        val declaration = declaration as? KSClassDeclaration ?: return false
        val qualifiedName = declaration.qualifiedName?.asString()
        return qualifiedName in SCALAR_QUALIFIED_NAMES || declaration.isSubclassOf("kotlin.Enum")
    }

    private fun KSPropertyDeclaration.hasAnnotation(shortName: String): Boolean {
        return annotations.any { it.shortName.asString() == shortName }
    }
}

private fun KSClassDeclaration.isTypeOrSubclassOf(qualifiedName: String): Boolean {
    return this.qualifiedName?.asString() == qualifiedName || isSubclassOf(qualifiedName)
}

private typealias KSTypePropertyKind = PropertyKind

private data class TrackedProperty(
    val source: KSPropertyDeclaration,
    val name: String,
    val type: com.google.devtools.ksp.symbol.KSTypeReference,
    val typeName: TypeName,
    val kind: PropertyKind,
    val mutable: Boolean,
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
    UNSUPPORTED,
}

private fun TrackedProperty.toPropertySpec(): PropertySpec {
    val builder = PropertySpec.builder(name, typeName)
    return when (kind) {
        PropertyKind.SCALAR -> {
            if (mutable) {
                builder.mutable(true)
                    .delegate(CodeBlock.of("%M(ctx.path(%S), source.%N, ctx.queue)", TRACKED_VALUE, name, name))
                    .build()
            } else {
                builder.initializer("source.%N", name).build()
            }
        }

        PropertyKind.MAP -> builder
            .delegate(CodeBlock.of("%M(ctx.path(%S), source.%N, ctx.queue)", TRACKED_MAP, name, name))
            .build()

        PropertyKind.LIST -> builder
            .delegate(CodeBlock.of("%M(ctx.path(%S), source.%N, ctx.queue)", TRACKED_LIST, name, name))
            .build()

        PropertyKind.SET -> builder
            .delegate(CodeBlock.of("%M(ctx.path(%S), source.%N, ctx.queue)", TRACKED_SET, name, name))
            .build()

        PropertyKind.DEQUE -> builder
            .delegate(CodeBlock.of("%M(ctx.path(%S), source.%N, ctx.queue)", TRACKED_DEQUE, name, name))
            .build()

        PropertyKind.INT_ARRAY -> builder
            .delegate(CodeBlock.of("%M(ctx.path(%S), source.%N, ctx.queue)", TRACKED_INT_ARRAY_FUN, name, name))
            .build()

        PropertyKind.LONG_ARRAY -> builder
            .delegate(CodeBlock.of("%M(ctx.path(%S), source.%N, ctx.queue)", TRACKED_LONG_ARRAY_FUN, name, name))
            .build()

        PropertyKind.BOOLEAN_ARRAY -> builder
            .delegate(CodeBlock.of("%M(ctx.path(%S), source.%N, ctx.queue)", TRACKED_BOOLEAN_ARRAY_FUN, name, name))
            .build()

        PropertyKind.DOUBLE_ARRAY -> builder
            .delegate(CodeBlock.of("%M(ctx.path(%S), source.%N, ctx.queue)", TRACKED_DOUBLE_ARRAY_FUN, name, name))
            .build()

        PropertyKind.FLOAT_ARRAY -> builder
            .delegate(CodeBlock.of("%M(ctx.path(%S), source.%N, ctx.queue)", TRACKED_FLOAT_ARRAY_FUN, name, name))
            .build()

        PropertyKind.UNSUPPORTED -> error("Unsupported property kind")
    }
}

private fun List<TrackedProperty>.toPersistentValueFunction(idPropertyName: String): FunSpec {
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
    val propertyByName = properties.associateBy { it.name }
    val constructor = primaryConstructor
    val constructorParameters = constructor?.parameters.orEmpty()
    return FunSpec.builder("toEntity")
        .addModifiers(KModifier.OVERRIDE)
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
private val PENDING_WRITE_QUEUE = ClassName("com.mikai233.common.db.tracked", "PendingWriteQueue")
private val TRACKED_INT_ARRAY = ClassName("com.mikai233.common.db.tracked", "TrackedIntArray")
private val TRACKED_LONG_ARRAY = ClassName("com.mikai233.common.db.tracked", "TrackedLongArray")
private val TRACKED_BOOLEAN_ARRAY = ClassName("com.mikai233.common.db.tracked", "TrackedBooleanArray")
private val TRACKED_DOUBLE_ARRAY = ClassName("com.mikai233.common.db.tracked", "TrackedDoubleArray")
private val TRACKED_FLOAT_ARRAY = ClassName("com.mikai233.common.db.tracked", "TrackedFloatArray")
private val MUTABLE_MAP = ClassName("kotlin.collections", "MutableMap")
private val MUTABLE_LIST = ClassName("kotlin.collections", "MutableList")
private val MUTABLE_SET = ClassName("kotlin.collections", "MutableSet")
private val DEQUE = ClassName("java.util", "Deque")

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
