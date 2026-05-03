package com.mikai233.messageksp

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import com.mikai233.common.message.catalog.CatalogDispatcherKind
import com.mikai233.common.message.catalog.GatewayEntityIdSource
import com.mikai233.common.message.catalog.GatewayFieldInjection
import com.mikai233.common.message.catalog.GatewayInjectionSource
import com.mikai233.common.message.catalog.GatewayRouteHintCatalog
import com.mikai233.common.message.catalog.GatewayRouteHintEntry
import com.mikai233.common.message.catalog.GatewayRouteTarget
import com.mikai233.common.message.catalog.MessageCatalog
import com.mikai233.common.message.catalog.MessageCatalogEntry
import java.util.Locale

private data class ModuleTypes(
    val handlerContext: ClassName,
    val registry: ClassName,
)

private data class HandlerBinding(
    val rootPackage: String,
    val handler: KSClassDeclaration,
    val messageClassName: ClassName,
    val dispatcher: CatalogDispatcherKind,
    val sourceFile: KSFile,
    val gatewayRoute: GatewayRouteBinding?,
)

private data class GatewayRouteBinding(
    val target: GatewayRouteTarget,
    val entityIdSource: GatewayEntityIdSource,
    val entityIdField: String,
    val injectRouteEntityIdTo: List<String>,
    val injectSessionPlayerIdTo: List<String>,
    val injectSessionWorldIdTo: List<String>,
    val clearFields: List<String>,
)

class MessageCatalogProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    private var generated = false
    private val annotationName = "com.mikai233.common.annotation.AsteriaMessageHandler"
    private val gatewayRouteAnnotationName = "com.mikai233.common.annotation.AsteriaGatewayRoute"

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) {
            return emptyList()
        }
        val symbols = resolver.getSymbolsWithAnnotation(annotationName).toList()
        val deferred = symbols.filterNot { it is KSClassDeclaration }
        val bindings = symbols
            .filterIsInstance<KSClassDeclaration>()
            .mapNotNull { declaration -> declaration.toBinding(declaration.containingFile ?: return@mapNotNull null) }
            .toList()
        if (bindings.isEmpty()) {
            generated = true
            return deferred
        }
        val bindingsByRoot = bindings.groupBy(HandlerBinding::rootPackage)
        for ((rootPackage, rootBindings) in bindingsByRoot) {
            generateCatalog(rootPackage, rootBindings)
            generateDispatchers(rootPackage, rootBindings)
            generateGatewayRouteHints(rootPackage, rootBindings)
            generateGatewayRouteMetadata(rootPackage, rootBindings)
        }
        generated = true
        return deferred
    }

    private fun KSClassDeclaration.toBinding(sourceFile: KSFile): HandlerBinding? {
        if (classKind != ClassKind.CLASS || isAbstract()) {
            return null
        }
        val packageName = packageName.asString()
        if (!packageName.contains(".handler.") || !simpleName.asString().endsWith("Handler")) {
            return null
        }
        if (
            !packageName.contains(".handler.event") &&
            !packageName.contains(".handler.message.") &&
            !packageName.contains(".handler.protocol.")
        ) {
            return null
        }
        val rootPackage = packageName.substringBefore(".handler.")
        val handleFunction = getDeclaredFunctions()
            .firstOrNull { function ->
                function.simpleName.asString() == "handle" &&
                    function.parameters.size == 2
            } ?: return null
        val messageType = handleFunction.parameters[1].type.resolve()
        val messageDeclaration = messageType.declaration as? KSClassDeclaration ?: return null
        val annotation = annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == annotationName
        } ?: return null
        val dispatcher = annotation.enumArgument("dispatcher", CatalogDispatcherKind::valueOf)
        if (dispatcher == CatalogDispatcherKind.PROTOBUF && !messageDeclaration.isGeneratedMessage()) {
            logger.error(
                "handler ${qualifiedName?.asString()} declares PROTOBUF dispatcher but message ${messageDeclaration.qualifiedName?.asString()} is not a protobuf GeneratedMessage",
                this,
            )
            return null
        }
        return HandlerBinding(
            rootPackage = rootPackage,
            handler = this,
            messageClassName = messageDeclaration.toClassName(),
            dispatcher = dispatcher,
            sourceFile = sourceFile,
            gatewayRoute = gatewayRouteBinding(),
        )
    }

    private fun KSClassDeclaration.isAbstract(): Boolean {
        return Modifier.ABSTRACT in modifiers
    }

    private fun KSClassDeclaration.isGeneratedMessage(): Boolean {
        val qualifiedName = qualifiedName?.asString()
        if (qualifiedName == "com.google.protobuf.GeneratedMessage") {
            return true
        }
        return getAllSuperTypes().any { type ->
            (type.declaration as? KSClassDeclaration)?.qualifiedName?.asString() == "com.google.protobuf.GeneratedMessage"
        }
    }

    private fun generateCatalog(rootPackage: String, bindings: List<HandlerBinding>) {
        val moduleName = rootPackage.substringAfterLast('.')
            .replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            }
        val generatedPackage = "$rootPackage.generated"
        val generatedType = TypeSpec.objectBuilder("Generated${moduleName}MessageCatalog")
            .addSuperinterface(MessageCatalog::class)
            .addProperty(
                PropertySpec.builder(
                    "bindings",
                    List::class.asClassName().parameterizedBy(MessageCatalogEntry::class.asClassName()),
                )
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(buildBindingsInitializer(bindings))
                    .build(),
            )
            .build()
        FileSpec.builder(generatedPackage, "Generated${moduleName}MessageCatalog")
            .addType(generatedType)
            .build()
            .writeTo(
                codeGenerator = codeGenerator,
                dependencies = Dependencies(
                    aggregating = false,
                    sources = bindings.map(HandlerBinding::sourceFile).toTypedArray(),
                ),
            )
    }

    private fun generateDispatchers(rootPackage: String, bindings: List<HandlerBinding>) {
        val moduleName = rootPackage.substringAfterLast('.')
            .replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            }
        val moduleTypes = moduleTypes(rootPackage) ?: return
        val generatedPackage = "$rootPackage.generated"
        val dispatcherType = ClassName("io.github.realmlabs.asteria.message", "MessageDispatcher")
        val generatedMessageType = ClassName("com.google.protobuf", "GeneratedMessage")
        val generatedType = TypeSpec.objectBuilder("Generated${moduleName}NodeDispatchers")
            .apply {
                bindings.map { it.dispatcher }.distinct().sortedBy { it.name }.forEach { dispatcherKind ->
                    val messageSuperType = when (dispatcherKind) {
                        CatalogDispatcherKind.PROTOBUF -> generatedMessageType
                        CatalogDispatcherKind.INTERNAL -> Any::class.asClassName()
                    }
                    val dispatcherBindings = bindings.filter { it.dispatcher == dispatcherKind }
                    addProperty(
                        PropertySpec.builder(
                            dispatcherKind.name,
                            dispatcherType.parameterizedBy(moduleTypes.handlerContext, messageSuperType),
                        )
                            .initializer(buildDispatcherExpression(moduleTypes, dispatcherBindings, messageSuperType))
                            .build(),
                    )
                }
            }
            .build()
        FileSpec.builder(generatedPackage, "Generated${moduleName}NodeDispatchers")
            .addType(generatedType)
            .build()
            .writeTo(
                codeGenerator = codeGenerator,
                dependencies = Dependencies(
                    aggregating = false,
                    sources = bindings.map(HandlerBinding::sourceFile).toTypedArray(),
                ),
            )
    }

    private fun generateGatewayRouteHints(rootPackage: String, bindings: List<HandlerBinding>) {
        val gatewayBindings = bindings.filter { it.gatewayRoute != null }
        if (gatewayBindings.isEmpty()) {
            return
        }
        val moduleName = rootPackage.substringAfterLast('.')
            .replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            }
        val generatedPackage = "$rootPackage.generated"
        val generatedType = TypeSpec.objectBuilder("Generated${moduleName}GatewayRouteHints")
            .addSuperinterface(GatewayRouteHintCatalog::class)
            .addProperty(
                PropertySpec.builder(
                    "routes",
                    List::class.asClassName().parameterizedBy(GatewayRouteHintEntry::class.asClassName()),
                )
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(buildGatewayRouteInitializer(gatewayBindings))
                    .build(),
            )
            .build()
        FileSpec.builder(generatedPackage, "Generated${moduleName}GatewayRouteHints")
            .addType(generatedType)
            .build()
            .writeTo(
                codeGenerator = codeGenerator,
                dependencies = Dependencies(
                    aggregating = false,
                    sources = gatewayBindings.map(HandlerBinding::sourceFile).toTypedArray(),
                ),
            )
    }

    private fun generateGatewayRouteMetadata(rootPackage: String, bindings: List<HandlerBinding>) {
        val gatewayBindings = bindings.filter { it.gatewayRoute != null }
        if (gatewayBindings.isEmpty()) {
            return
        }
        val moduleName = rootPackage.substringAfterLast('.').lowercase(Locale.getDefault())
        val output = codeGenerator.createNewFile(
            dependencies = Dependencies(
                aggregating = false,
                sources = gatewayBindings.map(HandlerBinding::sourceFile).toTypedArray(),
            ),
            packageName = "META-INF/antares/gateway-route-hints",
            fileName = moduleName,
            extensionName = "json",
        )
        output.bufferedWriter().use { writer ->
            writer.appendLine("{")
            writer.appendLine("  \"module\": \"$moduleName\",")
            writer.appendLine("  \"routes\": [")
            gatewayBindings.sortedBy { it.messageClassName.canonicalName }.forEachIndexed { index, binding ->
                val route = binding.gatewayRoute ?: return@forEachIndexed
                writer.appendLine("    {")
                writer.appendLine("      \"messageType\": \"${binding.messageClassName.canonicalName}\",")
                writer.appendLine("      \"handlerType\": \"${binding.handler.toClassName().canonicalName}\",")
                writer.appendLine("      \"target\": \"${route.target.name}\",")
                writer.appendLine("      \"entityIdSource\": \"${route.entityIdSource.name}\",")
                writer.appendLine("      \"entityIdField\": \"${route.entityIdField}\",")
                writer.appendLine("      \"injectRouteEntityIdTo\": ${stringArrayJson(route.injectRouteEntityIdTo)},")
                writer.appendLine("      \"injectSessionPlayerIdTo\": ${stringArrayJson(route.injectSessionPlayerIdTo)},")
                writer.appendLine("      \"injectSessionWorldIdTo\": ${stringArrayJson(route.injectSessionWorldIdTo)},")
                writer.appendLine("      \"clearFields\": ${stringArrayJson(route.clearFields)}")
                writer.append("    }")
                if (index != gatewayBindings.lastIndex) {
                    writer.append(',')
                }
                writer.appendLine()
            }
            writer.appendLine("  ]")
            writer.appendLine("}")
        }
    }

    private fun buildDispatcherBody(
        moduleTypes: ModuleTypes,
        bindings: List<HandlerBinding>,
        messageSuperType: ClassName,
    ): CodeBlock {
        val builder = CodeBlock.builder()
        builder.addStatement("return %L", buildDispatcherExpression(moduleTypes, bindings, messageSuperType))
        return builder.build()
    }

    private fun buildDispatcherExpression(
        moduleTypes: ModuleTypes,
        bindings: List<HandlerBinding>,
        messageSuperType: ClassName,
    ): CodeBlock {
        val registryClass = moduleTypes.registry
        val dispatcherClass = ClassName("io.github.realmlabs.asteria.message", "MessageDispatcher")
        val builder = CodeBlock.builder()
        builder.add("%T(%T().apply {\n", dispatcherClass, registryClass.parameterizedBy(messageSuperType))
        bindings.sortedBy { it.messageClassName.canonicalName }.forEach { binding ->
            val handlerExpression = instantiateExpression(binding.handler)
            builder.add(
                "  register(%T::class, %L::handle)\n",
                binding.messageClassName,
                handlerExpression,
            )
        }
        builder.add("})")
        return builder.build()
    }

    private fun instantiateExpression(type: KSClassDeclaration): CodeBlock {
        val constructor = pickConstructor(type)
            ?: error("no constructible constructor found for ${type.qualifiedName?.asString()}")
        val params = constructor.parameters
        if (params.isEmpty()) {
            return CodeBlock.of("%T()", type.toClassName())
        }
        val builder = CodeBlock.builder()
        builder.add("%T(", type.toClassName())
        params.forEachIndexed { index, parameter ->
            val dependency = parameter.type.resolve().declaration as? KSClassDeclaration
                ?: error("unsupported dependency type for ${type.qualifiedName?.asString()}")
            builder.add("%L", instantiateExpression(dependency))
            if (index != params.lastIndex) {
                builder.add(", ")
            }
        }
        builder.add(")")
        return builder.build()
    }

    private fun pickConstructor(type: KSClassDeclaration): KSFunctionDeclaration? {
        return type.primaryConstructor
            ?: type.getConstructors().singleOrNull()
            ?: type.getConstructors().firstOrNull { it.parameters.isEmpty() }
    }

    private fun KSClassDeclaration.gatewayRouteBinding(): GatewayRouteBinding? {
        val annotation = annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == gatewayRouteAnnotationName
        } ?: return null
        return GatewayRouteBinding(
            target = annotation.enumArgument("target", GatewayRouteTarget::valueOf),
            entityIdSource = annotation.enumArgument("entityIdSource", GatewayEntityIdSource::valueOf),
            entityIdField = annotation.stringArgument("entityIdField"),
            injectRouteEntityIdTo = annotation.stringListArgument("injectRouteEntityIdTo"),
            injectSessionPlayerIdTo = annotation.stringListArgument("injectSessionPlayerIdTo"),
            injectSessionWorldIdTo = annotation.stringListArgument("injectSessionWorldIdTo"),
            clearFields = annotation.stringListArgument("clearFields"),
        )
    }

    private fun buildGatewayRouteInitializer(bindings: List<HandlerBinding>): CodeBlock {
        val builder = CodeBlock.builder()
        builder.add("listOf(\n")
        bindings.sortedBy { it.messageClassName.canonicalName }.forEachIndexed { index, binding ->
            val route = binding.gatewayRoute ?: return@forEachIndexed
            builder.add(
                "  %T(\n    messageClass = %T::class,\n    handlerClass = %T::class,\n    target = %T.%L,\n    entityIdSource = %T.%L,\n    entityIdField = %S,\n    injections = %L,\n  )",
                GatewayRouteHintEntry::class,
                binding.messageClassName,
                binding.handler.toClassName(),
                GatewayRouteTarget::class,
                route.target.name,
                GatewayEntityIdSource::class,
                route.entityIdSource.name,
                route.entityIdField,
                buildInjectionsCode(route),
            )
            if (index != bindings.lastIndex) {
                builder.add(",\n")
            } else {
                builder.add("\n")
            }
        }
        builder.add(")")
        return builder.build()
    }

    private fun buildInjectionsCode(route: GatewayRouteBinding): CodeBlock {
        val injections = buildList {
            route.injectRouteEntityIdTo.forEach { add(it to GatewayInjectionSource.ROUTE_ENTITY_ID) }
            route.injectSessionPlayerIdTo.forEach { add(it to GatewayInjectionSource.SESSION_PLAYER_ID) }
            route.injectSessionWorldIdTo.forEach { add(it to GatewayInjectionSource.SESSION_WORLD_ID) }
            route.clearFields.forEach { add(it to GatewayInjectionSource.CLEAR) }
        }
        val builder = CodeBlock.builder()
        builder.add("listOf(")
        injections.forEachIndexed { index, (field, source) ->
            builder.add(
                "%T(field = %S, source = %T.%L)",
                GatewayFieldInjection::class,
                field,
                GatewayInjectionSource::class,
                source.name,
            )
            if (index != injections.lastIndex) {
                builder.add(", ")
            }
        }
        builder.add(")")
        return builder.build()
    }

    private fun KSAnnotation.stringArgument(name: String): String {
        return arguments.firstOrNull { it.name?.asString() == name }?.value as? String ?: ""
    }

    private fun KSAnnotation.stringListArgument(name: String): List<String> {
        @Suppress("UNCHECKED_CAST")
        return (arguments.firstOrNull { it.name?.asString() == name }?.value as? List<String>).orEmpty()
    }

    private fun <T> KSAnnotation.enumArgument(name: String, mapper: (String) -> T): T {
        val raw = arguments.firstOrNull { it.name?.asString() == name }?.value
            ?: error("missing annotation argument $name")
        val value = raw.toString().substringAfterLast('.')
        return mapper(value)
    }

    private fun stringArrayJson(values: List<String>): String {
        return values.joinToString(prefix = "[", postfix = "]") { value -> "\"$value\"" }
    }

    private fun moduleTypes(rootPackage: String): ModuleTypes? {
        return when {
            rootPackage.endsWith(".player") -> ModuleTypes(
                handlerContext = ClassName(rootPackage, "PlayerHandlerContext"),
                registry = ClassName(rootPackage, "PlayerMessageHandlerRegistry"),
            )

            rootPackage.endsWith(".world") -> ModuleTypes(
                handlerContext = ClassName(rootPackage, "WorldHandlerContext"),
                registry = ClassName(rootPackage, "WorldMessageHandlerRegistry"),
            )

            rootPackage.endsWith(".gate") -> ModuleTypes(
                handlerContext = ClassName(rootPackage, "ChannelHandlerContext"),
                registry = ClassName(rootPackage, "ChannelMessageHandlerRegistry"),
            )

            else -> {
                logger.error("unsupported root package for generated dispatchers: $rootPackage")
                null
            }
        }
    }

    private fun buildBindingsInitializer(bindings: List<HandlerBinding>): CodeBlock {
        val builder = CodeBlock.builder()
        builder.add("listOf(\n")
        bindings.sortedWith(compareBy({ it.dispatcher.name }, { it.messageClassName.canonicalName }))
            .forEachIndexed { index, binding ->
                builder.add(
                    "  %T(\n    messageClass = %T::class,\n    handlerClass = %T::class,\n    dispatcher = %T.%L,\n  )",
                    MessageCatalogEntry::class,
                    binding.messageClassName,
                    binding.handler.toClassName(),
                    CatalogDispatcherKind::class,
                    binding.dispatcher.name,
                )
                if (index != bindings.lastIndex) {
                    builder.add(",\n")
                } else {
                    builder.add("\n")
                }
            }
        builder.add(")")
        return builder.build()
    }
}
