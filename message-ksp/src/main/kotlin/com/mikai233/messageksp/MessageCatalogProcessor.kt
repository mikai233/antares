package com.mikai233.messageksp

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.Modifier
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
import com.mikai233.common.message.catalog.GatewayEntityIdSource
import com.mikai233.common.message.catalog.GatewayFieldInjection
import com.mikai233.common.message.catalog.GatewayInjectionSource
import com.mikai233.common.message.catalog.GatewayRouteHintCatalog
import com.mikai233.common.message.catalog.GatewayRouteHintEntry
import com.mikai233.common.message.catalog.GatewayRouteTarget
import java.util.Locale

private data class HandlerBinding(
    val rootPackage: String,
    val handler: KSClassDeclaration,
    val messageClassName: ClassName,
    val sourceFile: KSFile,
    val gatewayRoute: GatewayRouteBinding,
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
    private val gatewayRouteAnnotationName = "com.mikai233.common.annotation.AsteriaGatewayRoute"

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) {
            return emptyList()
        }
        val symbols = resolver.getSymbolsWithAnnotation(gatewayRouteAnnotationName).toList()
        val deferred = symbols.filterNot { it is KSClassDeclaration }
        val bindings = symbols
            .filterIsInstance<KSClassDeclaration>()
            .mapNotNull { declaration -> declaration.toBinding(declaration.containingFile ?: return@mapNotNull null) }
            .toList()
        if (bindings.isEmpty()) {
            generated = true
            return deferred
        }
        bindings.groupBy(HandlerBinding::rootPackage).forEach { (rootPackage, rootBindings) ->
            generateGatewayRouteHints(rootPackage, rootBindings)
            generateGatewayRouteMetadata(rootPackage, rootBindings)
        }
        generated = true
        return deferred
    }

    private fun KSClassDeclaration.toBinding(sourceFile: KSFile): HandlerBinding? {
        if (classKind != ClassKind.CLASS || Modifier.ABSTRACT in modifiers) {
            return null
        }
        val packageName = packageName.asString()
        if (!packageName.contains(".handler.") || !simpleName.asString().endsWith("Handler")) {
            return null
        }
        val rootPackage = packageName.substringBefore(".handler.")
        val handleFunction = getDeclaredFunctions().firstOrNull { function ->
            function.simpleName.asString() == "handle" && function.parameters.size == 2
        } ?: run {
            logger.error("gateway route handler must define handle(context, message): ${qualifiedName?.asString()}", this)
            return null
        }
        val messageDeclaration = handleFunction.parameters[1].type.resolve().declaration as? KSClassDeclaration ?: run {
            logger.error("gateway route handler message must be a class type: ${qualifiedName?.asString()}", this)
            return null
        }
        return HandlerBinding(
            rootPackage = rootPackage,
            handler = this,
            messageClassName = messageDeclaration.toClassName(),
            sourceFile = sourceFile,
            gatewayRoute = gatewayRouteBinding() ?: return null,
        )
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

    private fun generateGatewayRouteHints(rootPackage: String, bindings: List<HandlerBinding>) {
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
                    .initializer(buildGatewayRouteInitializer(bindings))
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
                    sources = bindings.map(HandlerBinding::sourceFile).toTypedArray(),
                ),
            )
    }

    private fun generateGatewayRouteMetadata(rootPackage: String, bindings: List<HandlerBinding>) {
        val moduleName = rootPackage.substringAfterLast('.').lowercase(Locale.getDefault())
        val output = codeGenerator.createNewFile(
            dependencies = Dependencies(
                aggregating = false,
                sources = bindings.map(HandlerBinding::sourceFile).toTypedArray(),
            ),
            packageName = "META-INF/antares/gateway-route-hints",
            fileName = moduleName,
            extensionName = "json",
        )
        output.bufferedWriter().use { writer ->
            writer.appendLine("{")
            writer.appendLine("  \"module\": \"$moduleName\",")
            writer.appendLine("  \"routes\": [")
            bindings.sortedBy { it.messageClassName.canonicalName }.forEachIndexed { index, binding ->
                val route = binding.gatewayRoute
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
                if (index != bindings.lastIndex) {
                    writer.append(',')
                }
                writer.appendLine()
            }
            writer.appendLine("  ]")
            writer.appendLine("}")
        }
    }

    private fun buildGatewayRouteInitializer(bindings: List<HandlerBinding>): CodeBlock {
        val builder = CodeBlock.builder()
        builder.add("listOf(\n")
        bindings.sortedBy { it.messageClassName.canonicalName }.forEachIndexed { index, binding ->
            val route = binding.gatewayRoute
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
}
