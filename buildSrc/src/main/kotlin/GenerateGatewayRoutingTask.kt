import com.fasterxml.jackson.databind.ObjectMapper
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

private const val ROUTE_CHUNK_SIZE = 200

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
                        route = routeNode.path("route").asText(),
                        entityId = routeNode.path("entityId").takeUnless { it.isNull }?.asText(),
                        inject = routeNode.path("inject").map { it.asText() },
                        clearFields = routeNode.path("clearFields").map { it.asText() },
                    )
                }
            }
            .sortedBy { it.messageId }
        val outputRoot = outputDir.get().asFile
        val generatedDir = outputRoot.resolve("com/mikai233/gate/generated")
        if (generatedDir.exists()) {
            generatedDir.deleteRecursively()
        }
        outputRoot.mkdirs()
        buildFiles(routes).forEach { it.writeTo(outputRoot) }
    }

    private fun buildFiles(routes: List<GatewayRouteSpec>): List<FileSpec> {
        val files = mutableListOf<FileSpec>()
        val routeSpecType = ClassName("com.mikai233.gate.generated", "GeneratedGatewayRouting", "GeneratedGatewayRouteSpec")
        val mapType = Map::class.asClassName().parameterizedBy(Int::class.asClassName(), routeSpecType)
        val gatewaySessionContext = ClassName("io.github.realmlabs.asteria.gateway", "GatewaySessionContext")
        val gatewayRoute = ClassName("io.github.realmlabs.asteria.gateway", "GatewayRoute")
        val clientProtobuf = ClassName("com.mikai233.common.message", "ClientProtobuf")

        files += FileSpec.builder("com.mikai233.gate.generated", "GeneratedGatewayRouting")
            .addType(
                TypeSpec.objectBuilder("GeneratedGatewayRouting")
                    .addType(
                        TypeSpec.classBuilder("GeneratedGatewayRouteSpec")
                            .primaryConstructor(
                                FunSpec.constructorBuilder()
                                    .addParameter("route", String::class)
                                    .addParameter("entityId", String::class.asClassName().copy(nullable = true))
                                    .addParameter(
                                        "inject",
                                        List::class.asClassName().parameterizedBy(String::class.asClassName()),
                                    )
                                    .addParameter(
                                        "clearFields",
                                        List::class.asClassName().parameterizedBy(String::class.asClassName()),
                                    )
                                    .build(),
                            )
                            .addProperty(PropertySpec.builder("route", String::class).initializer("route").build())
                            .addProperty(
                                PropertySpec.builder("entityId", String::class.asClassName().copy(nullable = true))
                                    .initializer("entityId")
                                    .build(),
                            )
                            .addProperty(
                                PropertySpec.builder(
                                    "inject",
                                    List::class.asClassName().parameterizedBy(String::class.asClassName()),
                                ).initializer("inject").build(),
                            )
                            .addProperty(
                                PropertySpec.builder(
                                    "clearFields",
                                    List::class.asClassName().parameterizedBy(String::class.asClassName()),
                                ).initializer("clearFields").build(),
                            )
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("routesById", mapType)
                            .initializer(buildRoutesById(routes))
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
                    .addFunction(buildResolveTargetHelper())
                    .addFunction(buildResolveEntityIdHelper())
                    .addFunction(buildResolveLongSourceHelper())
                    .addFunction(buildReadMessageLongFieldHelper())
                    .addFunction(buildWriteLongFieldHelper())
                    .addFunction(buildClearFieldHelper())
                    .build(),
            )
            .build()

        routes.chunked(ROUTE_CHUNK_SIZE).forEachIndexed { index, chunk ->
            files += buildChunkFile(index, chunk, routeSpecType)
        }
        return files
    }

    private fun buildRoutesById(routes: List<GatewayRouteSpec>): CodeBlock {
        val chunkCount = routes.chunked(ROUTE_CHUNK_SIZE).size
        val builder = CodeBlock.builder()
        builder.add("buildMap {\n")
        repeat(chunkCount) { index ->
            builder.add("  putAll(%T.routesById)\n", ClassName("com.mikai233.gate.generated", "GeneratedGatewayRoutingChunk$index"))
        }
        builder.add("}")
        return builder.build()
    }

    private fun buildChunkFile(
        index: Int,
        routes: List<GatewayRouteSpec>,
        routeSpecType: ClassName,
    ): FileSpec {
        val mapType = Map::class.asClassName().parameterizedBy(Int::class.asClassName(), routeSpecType)
        return FileSpec.builder("com.mikai233.gate.generated", "GeneratedGatewayRoutingChunk$index")
            .addType(
                TypeSpec.objectBuilder("GeneratedGatewayRoutingChunk$index")
                    .addProperty(
                        PropertySpec.builder("routesById", mapType)
                            .initializer(buildChunkRoutesById(routes, routeSpecType))
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    private fun buildChunkRoutesById(
        routes: List<GatewayRouteSpec>,
        routeSpecType: ClassName,
    ): CodeBlock {
        val builder = CodeBlock.builder()
        builder.add("mapOf(\n")
        routes.forEachIndexed { index, route ->
            builder.add(
                "  %L to %T(route = %S, entityId = %L, inject = %L, clearFields = %L)",
                route.messageId,
                routeSpecType,
                route.route,
                route.entityId?.let { "\"$it\"" } ?: "null",
                buildStringListCode(route.inject),
                buildStringListCode(route.clearFields),
            )
            if (index != routes.lastIndex) builder.add(",\n") else builder.add("\n")
        }
        builder.add(")")
        return builder.build()
    }

    private fun buildStringListCode(values: List<String>): CodeBlock {
        if (values.isEmpty()) return CodeBlock.of("emptyList()")
        val builder = CodeBlock.builder()
        builder.add("listOf(")
        values.forEachIndexed { index, value ->
            if (index > 0) builder.add(", ")
            builder.add("%S", value)
        }
        builder.add(")")
        return builder.build()
    }

    private fun buildResolveCode(): CodeBlock {
        val gatewayRoute = ClassName("io.github.realmlabs.asteria.gateway", "GatewayRoute")
        val builder = CodeBlock.builder()
        builder.addStatement("val spec = routesById[packet.id] ?: return null")
        builder.addStatement("val target = resolveTarget(spec.route)")
        builder.addStatement("val entityId = if (spec.route == %S) null else resolveEntityId(spec, context, packet)", "gateway-local")
        builder.addStatement("return %T(target, entityId)", gatewayRoute)
        return builder.build()
    }

    private fun buildEntityMessageCode(routes: List<GatewayRouteSpec>): CodeBlock {
        val builder = CodeBlock.builder()
        if (routes.none { !it.hasNoPatch() }) {
            builder.addStatement("return null")
            return builder.build()
        }
        builder.addStatement("val spec = routesById[packet.id] ?: return null")
        builder.beginControlFlow("if (spec.inject.isEmpty() && spec.clearFields.isEmpty())")
        builder.addStatement("return null")
        builder.endControlFlow()
        builder.addStatement("val builder = packet.message.toBuilder()")
        builder.beginControlFlow("for (entry in spec.inject)")
        builder.addStatement("val field = entry.substringBefore(%S)", "=")
        builder.addStatement("val source = entry.substringAfter(%S)", "=")
        builder.addStatement("writeLongField(builder, field, resolveLongSource(source, route, context, packet))")
        builder.endControlFlow()
        builder.beginControlFlow("for (field in spec.clearFields)")
        builder.addStatement("clearField(builder, field)")
        builder.endControlFlow()
        builder.addStatement("return builder.build()")
        return builder.build()
    }

    private fun buildResolveTargetHelper(): FunSpec {
        val routeTarget = ClassName("io.github.realmlabs.asteria.message", "RouteTarget")
        val entityKind = ClassName("io.github.realmlabs.asteria.core", "EntityKind")
        val gameEntityKinds = ClassName("com.mikai233.common.core", "GameEntityKinds")
        return FunSpec.builder("resolveTarget")
            .addModifiers(com.squareup.kotlinpoet.KModifier.PRIVATE)
            .addParameter("route", String::class)
            .returns(routeTarget)
            .beginControlFlow("return when (route)")
            .addStatement("%S -> %T.GatewayLocal", "gateway-local", routeTarget)
            .addStatement("%S -> %T.Entity(%T(%T.PlayerActor))", "player", routeTarget, entityKind, gameEntityKinds)
            .addStatement("%S -> %T.Entity(%T(%T.WorldActor))", "world", routeTarget, entityKind, gameEntityKinds)
            .addStatement("else -> error(%P + route)", "unsupported gateway route: ")
            .endControlFlow()
            .build()
    }

    private fun buildResolveEntityIdHelper(): FunSpec {
        val routeSpecType = ClassName("com.mikai233.gate.generated", "GeneratedGatewayRouting", "GeneratedGatewayRouteSpec")
        val gatewaySessionContext = ClassName("io.github.realmlabs.asteria.gateway", "GatewaySessionContext")
        val clientProtobuf = ClassName("com.mikai233.common.message", "ClientProtobuf")
        val gatewayRoute = ClassName("io.github.realmlabs.asteria.gateway", "GatewayRoute")
        return FunSpec.builder("resolveEntityId")
            .addModifiers(com.squareup.kotlinpoet.KModifier.PRIVATE)
            .addParameter("spec", routeSpecType)
            .addParameter("context", gatewaySessionContext)
            .addParameter("packet", clientProtobuf)
            .returns(Long::class)
            .addStatement("val route = %T(resolveTarget(spec.route))", gatewayRoute)
            .addStatement("return resolveLongSource(requireNotNull(spec.entityId), route, context, packet)")
            .build()
    }

    private fun buildResolveLongSourceHelper(): FunSpec {
        val gatewayRoute = ClassName("io.github.realmlabs.asteria.gateway", "GatewayRoute")
        val gatewaySessionContext = ClassName("io.github.realmlabs.asteria.gateway", "GatewaySessionContext")
        val clientProtobuf = ClassName("com.mikai233.common.message", "ClientProtobuf")
        return FunSpec.builder("resolveLongSource")
            .addModifiers(com.squareup.kotlinpoet.KModifier.PRIVATE)
            .addParameter("source", String::class)
            .addParameter("route", gatewayRoute)
            .addParameter("context", gatewaySessionContext)
            .addParameter("packet", clientProtobuf)
            .returns(Long::class)
            .beginControlFlow("return when")
            .addStatement("source.startsWith(%S) -> readMessageLongField(packet.message, source.removePrefix(%S))", "message:", "message:")
            .addStatement("source == %S -> requireNotNull(context.session.get(com.mikai233.gate.GatePlayerIdKey))", "session:player_id")
            .addStatement("source == %S -> requireNotNull(context.session.get(com.mikai233.gate.GateWorldIdKey))", "session:world_id")
            .addStatement("source == %S -> route.entityId as Long", "route.entity_id")
            .addStatement("source.isBlank() -> error(%P)", "route spec has no source")
            .addStatement("else -> error(%P + source)", "unsupported gateway source: ")
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
    val route: String,
    val entityId: String?,
    val inject: List<String>,
    val clearFields: List<String>,
) {
    fun hasNoPatch(): Boolean = inject.isEmpty() && clearFields.isEmpty()
}
