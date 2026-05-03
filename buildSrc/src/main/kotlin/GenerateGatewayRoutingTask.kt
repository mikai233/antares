import com.fasterxml.jackson.databind.ObjectMapper
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Locale

abstract class GenerateGatewayRoutingTask : DefaultTask() {
    @get:InputFiles
    abstract val metadataFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val mapper = ObjectMapper()
        val rpcRegistry = project.rootDir.resolve("proto/protocol/rpc-protocol.json")
        val messageIds = loadMessageIds(mapper, rpcRegistry)
        val routes = metadataFiles.files
            .filter(File::exists)
            .sortedBy { it.name }
            .flatMap { file ->
                val root = mapper.readTree(file)
                root.path("routes").map { routeNode ->
                    GatewayRouteSpec(
                        messageId = requireNotNull(messageIds[routeNode.path("messageType").asText()]) {
                            "message id not found for ${routeNode.path("messageType").asText()}"
                        },
                        messageType = routeNode.path("messageType").asText(),
                        target = routeNode.path("target").asText(),
                        entityIdSource = routeNode.path("entityIdSource").asText(),
                        entityIdField = routeNode.path("entityIdField").asText(),
                        injectRouteEntityIdTo = routeNode.path("injectRouteEntityIdTo").map { it.asText() },
                        injectSessionPlayerIdTo = routeNode.path("injectSessionPlayerIdTo").map { it.asText() },
                        injectSessionWorldIdTo = routeNode.path("injectSessionWorldIdTo").map { it.asText() },
                        clearFields = routeNode.path("clearFields").map { it.asText() },
                    )
                }
            }
        val outputRoot = outputDir.get().asFile
        val stalePath = outputRoot.resolve("com/mikai233/gate/generated/GeneratedGatewayRouting.kt")
        if (stalePath.isDirectory) {
            stalePath.deleteRecursively()
        }
        outputRoot.mkdirs()
        buildFile(routes).writeTo(outputRoot)
    }

    private fun buildFile(routes: List<GatewayRouteSpec>): FileSpec {
        val routeSpecType = ClassName("com.mikai233.gate.generated", "GeneratedGatewayRouting", "GeneratedGatewayRouteSpec")
        val mapType = Map::class.asClassName().parameterizedBy(Int::class.asClassName(), routeSpecType)
        val gatewaySessionContext = ClassName("io.github.realmlabs.asteria.gateway", "GatewaySessionContext")
        val gatewayRoute = ClassName("io.github.realmlabs.asteria.gateway", "GatewayRoute")
        val clientProtobuf = ClassName("com.mikai233.common.message", "ClientProtobuf")
        val targetEnum = ClassName("com.mikai233.common.message.catalog", "GatewayRouteTarget")
        val entityIdSourceEnum = ClassName("com.mikai233.common.message.catalog", "GatewayEntityIdSource")
        val fieldInjectionType = ClassName("com.mikai233.common.message.catalog", "GatewayFieldInjection")
        val injectionSourceEnum = ClassName("com.mikai233.common.message.catalog", "GatewayInjectionSource")
        val type = TypeSpec.objectBuilder("GeneratedGatewayRouting")
            .addType(
                TypeSpec.classBuilder("GeneratedGatewayRouteSpec")
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter("target", targetEnum)
                            .addParameter("entityIdSource", entityIdSourceEnum)
                            .addParameter("entityIdField", String::class)
                            .addParameter(
                                "injections",
                                List::class.asClassName().parameterizedBy(fieldInjectionType),
                            )
                            .build(),
                    )
                    .addProperty(PropertySpec.builder("target", targetEnum).initializer("target").build())
                    .addProperty(PropertySpec.builder("entityIdSource", entityIdSourceEnum).initializer("entityIdSource").build())
                    .addProperty(PropertySpec.builder("entityIdField", String::class).initializer("entityIdField").build())
                    .addProperty(
                        PropertySpec.builder(
                            "injections",
                            List::class.asClassName().parameterizedBy(fieldInjectionType),
                        ).initializer("injections").build(),
                    )
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("routesById", mapType)
                    .initializer(buildRoutesById(routes, routeSpecType, targetEnum, entityIdSourceEnum, fieldInjectionType, injectionSourceEnum))
                    .build(),
            )
            .addFunction(
                FunSpec.builder("resolve")
                    .addParameter("context", gatewaySessionContext)
                    .addParameter("packet", clientProtobuf)
                    .returns(gatewayRoute.copy(nullable = true))
                    .addCode(buildResolveCode())
                    .build(),
            )
            .addFunction(
                FunSpec.builder("entityMessage")
                    .addParameter("context", gatewaySessionContext)
                    .addParameter("route", gatewayRoute)
                    .addParameter("packet", clientProtobuf)
                    .returns(ClassName("kotlin", "Any").copy(nullable = true))
                    .addCode(buildEntityMessageCode(routes))
                    .build(),
            )
            .addFunction(buildResolveEntityIdHelper())
            .addFunction(buildReadMessageLongFieldHelper())
            .addFunction(buildWriteLongFieldHelper())
            .addFunction(buildClearFieldHelper())
            .build()
        return FileSpec.builder("com.mikai233.gate.generated", "GeneratedGatewayRouting")
            .addType(type)
            .build()
    }

    private fun buildRoutesById(
        routes: List<GatewayRouteSpec>,
        routeSpecType: ClassName,
        targetEnum: ClassName,
        entityIdSourceEnum: ClassName,
        fieldInjectionType: ClassName,
        injectionSourceEnum: ClassName,
    ): CodeBlock {
        val builder = CodeBlock.builder()
        builder.add("mapOf(\n")
        routes.sortedBy { it.messageId }.forEachIndexed { index, route ->
            builder.add(
                "  %L to %T(target = %T.%L, entityIdSource = %T.%L, entityIdField = %S, injections = %L)",
                route.messageId,
                routeSpecType,
                targetEnum,
                route.target,
                entityIdSourceEnum,
                route.entityIdSource,
                route.entityIdField,
                buildInjectionsCode(route, fieldInjectionType, injectionSourceEnum),
            )
            if (index != routes.lastIndex) {
                builder.add(",\n")
            } else {
                builder.add("\n")
            }
        }
        builder.add(")")
        return builder.build()
    }

    private fun buildInjectionsCode(
        route: GatewayRouteSpec,
        fieldInjectionType: ClassName,
        injectionSourceEnum: ClassName,
    ): CodeBlock {
        val builder = CodeBlock.builder()
        val injections = buildList {
            route.injectRouteEntityIdTo.forEach { add(it to "ROUTE_ENTITY_ID") }
            route.injectSessionPlayerIdTo.forEach { add(it to "SESSION_PLAYER_ID") }
            route.injectSessionWorldIdTo.forEach { add(it to "SESSION_WORLD_ID") }
            route.clearFields.forEach { add(it to "CLEAR") }
        }
        if (injections.isEmpty()) {
            builder.add("emptyList()")
            return builder.build()
        }
        builder.add("listOf(")
        injections.forEachIndexed { index, (field, source) ->
            if (index > 0) {
                builder.add(", ")
            }
            builder.add(
                "%T(field = %S, source = %T.%L)",
                fieldInjectionType,
                field,
                injectionSourceEnum,
                source,
            )
        }
        builder.add(")")
        return builder.build()
    }

    private fun buildResolveCode(): CodeBlock {
        val routeTarget = ClassName("io.github.realmlabs.asteria.message", "RouteTarget")
        val entityKind = ClassName("io.github.realmlabs.asteria.core", "EntityKind")
        val gatewayRoute = ClassName("io.github.realmlabs.asteria.gateway", "GatewayRoute")
        val gameEntityKinds = ClassName("com.mikai233.common.core", "GameEntityKinds")
        val gatewayRouteTarget = ClassName("com.mikai233.common.message.catalog", "GatewayRouteTarget")
        val gatewayEntityIdSource = ClassName("com.mikai233.common.message.catalog", "GatewayEntityIdSource")
        val builder = CodeBlock.builder()
        builder.addStatement(
            "val playerRouteTarget = %T.Entity(%T(%T.PlayerActor))",
            routeTarget,
            entityKind,
            gameEntityKinds,
        )
        builder.addStatement(
            "val worldRouteTarget = %T.Entity(%T(%T.WorldActor))",
            routeTarget,
            entityKind,
            gameEntityKinds,
        )
        builder.addStatement("val spec = routesById[packet.id] ?: return null")
        builder.beginControlFlow("return when (spec.target)")
        builder.addStatement("%T.GATEWAY_LOCAL -> %T(%T.GatewayLocal)", gatewayRouteTarget, gatewayRoute, routeTarget)
        builder.addStatement(
            "%T.PLAYER_ENTITY -> %T(playerRouteTarget, resolveEntityId(spec, context, packet))",
            gatewayRouteTarget,
            gatewayRoute,
        )
        builder.addStatement(
            "%T.WORLD_ENTITY -> %T(worldRouteTarget, resolveEntityId(spec, context, packet))",
            gatewayRouteTarget,
            gatewayRoute,
        )
        builder.endControlFlow()
        return builder.build()
    }

    private fun buildEntityMessageCode(routes: List<GatewayRouteSpec>): CodeBlock {
        val gatewayInjectionSource = ClassName("com.mikai233.common.message.catalog", "GatewayInjectionSource")
        val builder = CodeBlock.builder()
        val entityRouteIds = routes.filter { it.target == "PLAYER_ENTITY" || it.target == "WORLD_ENTITY" }
            .filterNot { it.hasNoInjection() }
            .map { it.messageId }
            .sorted()
        builder.addStatement("if (packet.id !in setOf(%L)) return null", entityRouteIds.joinToString())
        builder.addStatement("val spec = routesById[packet.id] ?: return null")
        builder.addStatement("val builder = packet.message.toBuilder()")
        builder.beginControlFlow("for (injection in spec.injections)")
        builder.beginControlFlow("when (injection.source)")
        builder.addStatement(
            "%T.ROUTE_ENTITY_ID -> writeLongField(builder, injection.field, route.entityId as Long)",
            gatewayInjectionSource,
        )
        builder.addStatement(
            "%T.SESSION_PLAYER_ID -> writeLongField(builder, injection.field, requireNotNull(context.session.get(com.mikai233.gate.GatePlayerIdKey)))",
            gatewayInjectionSource,
        )
        builder.addStatement(
            "%T.SESSION_WORLD_ID -> writeLongField(builder, injection.field, requireNotNull(context.session.get(com.mikai233.gate.GateWorldIdKey)))",
            gatewayInjectionSource,
        )
        builder.addStatement("%T.CLEAR -> clearField(builder, injection.field)", gatewayInjectionSource)
        builder.endControlFlow()
        builder.endControlFlow()
        builder.addStatement("return builder.build()")
        return builder.build()
    }

    private fun buildResolveEntityIdHelper(): FunSpec {
        val routeSpecType = ClassName("com.mikai233.gate.generated", "GeneratedGatewayRouting", "GeneratedGatewayRouteSpec")
        val gatewaySessionContext = ClassName("io.github.realmlabs.asteria.gateway", "GatewaySessionContext")
        val clientProtobuf = ClassName("com.mikai233.common.message", "ClientProtobuf")
        val gatewayEntityIdSource = ClassName("com.mikai233.common.message.catalog", "GatewayEntityIdSource")
        return FunSpec.builder("resolveEntityId")
            .addModifiers(com.squareup.kotlinpoet.KModifier.PRIVATE)
            .addParameter("spec", routeSpecType)
            .addParameter("context", gatewaySessionContext)
            .addParameter("packet", clientProtobuf)
            .returns(Long::class)
            .beginControlFlow("return when (spec.entityIdSource)")
            .addStatement("%T.MESSAGE_FIELD -> readMessageLongField(packet.message, spec.entityIdField)", gatewayEntityIdSource)
            .addStatement(
                "%T.SESSION_PLAYER_ID -> requireNotNull(context.session.get(com.mikai233.gate.GatePlayerIdKey))",
                gatewayEntityIdSource,
            )
            .addStatement(
                "%T.SESSION_WORLD_ID -> requireNotNull(context.session.get(com.mikai233.gate.GateWorldIdKey))",
                gatewayEntityIdSource,
            )
            .addStatement("%T.NONE -> error(%P)", gatewayEntityIdSource, "route spec has no entity id source")
            .endControlFlow()
            .build()
    }

    private fun buildReadMessageLongFieldHelper(): FunSpec {
        val generatedMessage = ClassName("com.google.protobuf", "GeneratedMessage")
        return FunSpec.builder("readMessageLongField")
            .addModifiers(com.squareup.kotlinpoet.KModifier.PRIVATE)
            .addParameter("message", generatedMessage)
            .addParameter("field", String::class)
            .returns(Long::class)
            .addStatement(
                "val getter = %P + field.split('_').filter { it.isNotBlank() }.joinToString(%P) { part -> part.replaceFirstChar { ch -> ch.titlecase() } }",
                "get",
                "",
            )
            .addStatement("val value = message.javaClass.getMethod(getter).invoke(message)")
            .addStatement("return (value as Number).toLong()")
            .build()
    }

    private fun buildWriteLongFieldHelper(): FunSpec {
        val messageBuilder = ClassName("com.google.protobuf", "Message", "Builder")
        return FunSpec.builder("writeLongField")
            .addModifiers(com.squareup.kotlinpoet.KModifier.PRIVATE)
            .addParameter("builder", messageBuilder)
            .addParameter("field", String::class)
            .addParameter("value", Long::class)
            .addStatement(
                "val setter = %P + field.split('_').filter { it.isNotBlank() }.joinToString(%P) { part -> part.replaceFirstChar { ch -> ch.titlecase() } }",
                "set",
                "",
            )
            .addStatement("builder::class.java.methods.first { it.name == setter && it.parameterCount == 1 }.invoke(builder, value)")
            .build()
    }

    private fun buildClearFieldHelper(): FunSpec {
        val messageBuilder = ClassName("com.google.protobuf", "Message", "Builder")
        return FunSpec.builder("clearField")
            .addModifiers(com.squareup.kotlinpoet.KModifier.PRIVATE)
            .addParameter("builder", messageBuilder)
            .addParameter("field", String::class)
            .addStatement(
                "val clearer = %P + field.split('_').filter { it.isNotBlank() }.joinToString(%P) { part -> part.replaceFirstChar { ch -> ch.titlecase() } }",
                "clear",
                "",
            )
            .addStatement("builder::class.java.getMethod(clearer).invoke(builder)")
            .build()
    }

    private fun loadMessageIds(mapper: ObjectMapper, file: File): Map<String, Int> {
        val root = mapper.readTree(file)
        return root.path("messages").associate { node ->
            node.path("type").asText() to node.path("id").asInt()
        }
    }
}

private data class GatewayRouteSpec(
    val messageId: Int,
    val messageType: String,
    val target: String,
    val entityIdSource: String,
    val entityIdField: String,
    val injectRouteEntityIdTo: List<String>,
    val injectSessionPlayerIdTo: List<String>,
    val injectSessionWorldIdTo: List<String>,
    val clearFields: List<String>,
) {
    fun hasNoInjection(): Boolean {
        return injectRouteEntityIdTo.isEmpty() &&
            injectSessionPlayerIdTo.isEmpty() &&
            injectSessionWorldIdTo.isEmpty() &&
            clearFields.isEmpty()
    }
}
