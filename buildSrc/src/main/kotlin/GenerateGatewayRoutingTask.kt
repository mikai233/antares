import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.DescriptorProtos
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

private const val ROUTE_CHUNK_SIZE = 200
private const val GATEWAY_LOCAL_ROUTE = "gateway-local"
private const val PLAYER_ROUTE = "player"
private const val WORLD_ROUTE = "world"
private const val MESSAGE_WORLD_ID_SOURCE = "message:world_id"
private const val ROUTE_ENTITY_ID_SOURCE = "route.entity_id"
private const val SESSION_PLAYER_ID_SOURCE = "session:player_id"
private const val SESSION_WORLD_ID_SOURCE = "session:world_id"
private const val PLAYER_ID_FIELD = "player_id"
private const val INJECT_PLAYER_ID_FROM_ROUTE_ENTITY_ID = "player_id=route.entity_id"

abstract class GenerateGatewayRoutingTask : DefaultTask() {
    @get:InputFiles
    abstract val metadataFiles: ConfigurableFileCollection

    @get:InputFile
    abstract val descriptorSetFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val mapper = ObjectMapper()
        val descriptorSet = DescriptorProtos.FileDescriptorSet.parseFrom(descriptorSetFile.get().asFile.inputStream())
        val messageIds = loadClientMessageIds(descriptorSet)
        val messageDescriptors = discoverGeneratedMessageDescriptors(descriptorSet)
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
                        entityId = routeNode.optionalText("entityId")
                            ?: defaultEntityId(routeNode.path("route").asText()),
                        inject = routeNode.optionalTextList("inject")
                            ?: defaultInject(
                                route = routeNode.path("route").asText(),
                                descriptor = messageDescriptors[routeNode.path("messageType").asText()],
                            ),
                        clearFields = routeNode.optionalTextList("clearFields") ?: emptyList(),
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
        val routeSpecType =
            ClassName("com.mikai233.gate.generated", "GeneratedGatewayRouting", "GeneratedGatewayRouteSpec")
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
            builder.add(
                "  putAll(%T.routesById)\n",
                ClassName("com.mikai233.gate.generated", "GeneratedGatewayRoutingChunk$index"),
            )
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
                "  %L to %T(route = %L, entityId = %L, inject = %L, clearFields = %L)",
                route.messageId,
                routeSpecType,
                buildRouteCode(route.route),
                buildRouteSourceCode(route.entityId),
                buildStringListCode(route.inject),
                buildStringListCode(route.clearFields),
            )
            if (index != routes.lastIndex) builder.add(",\n") else builder.add("\n")
        }
        builder.add(")")
        return builder.build()
    }

    private fun buildRouteCode(route: String): CodeBlock {
        val gatewayRoutes = ClassName("com.mikai233.common.message", "GatewayRoutes")
        return when (route) {
            GATEWAY_LOCAL_ROUTE -> CodeBlock.of("%T.GATEWAY_LOCAL", gatewayRoutes)
            PLAYER_ROUTE -> CodeBlock.of("%T.PLAYER", gatewayRoutes)
            WORLD_ROUTE -> CodeBlock.of("%T.WORLD", gatewayRoutes)
            else -> CodeBlock.of("%S", route)
        }
    }

    private fun buildRouteSourceCode(source: String?): CodeBlock {
        if (source == null) {
            return CodeBlock.of("null")
        }
        val gatewayRouteSources = ClassName("com.mikai233.common.message", "GatewayRouteSources")
        return when (source) {
            MESSAGE_WORLD_ID_SOURCE -> CodeBlock.of("%T.MESSAGE_WORLD_ID", gatewayRouteSources)
            ROUTE_ENTITY_ID_SOURCE -> CodeBlock.of("%T.ROUTE_ENTITY_ID", gatewayRouteSources)
            SESSION_PLAYER_ID_SOURCE -> CodeBlock.of("%T.SESSION_PLAYER_ID", gatewayRouteSources)
            SESSION_WORLD_ID_SOURCE -> CodeBlock.of("%T.SESSION_WORLD_ID", gatewayRouteSources)
            else -> CodeBlock.of("%S", source)
        }
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
        val gatewayRoutes = ClassName("com.mikai233.common.message", "GatewayRoutes")
        val builder = CodeBlock.builder()
        builder.addStatement("val spec = routesById[packet.id] ?: return null")
        builder.addStatement("val target = resolveTarget(spec.route)")
        builder.addStatement(
            "val entityId = if (spec.route == %T.GATEWAY_LOCAL) null else resolveEntityId(spec, context, packet)",
            gatewayRoutes,
        )
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
        val gameEntityKinds = ClassName("com.mikai233.common.runtime", "GameEntityKinds")
        val gatewayRoutes = ClassName("com.mikai233.common.message", "GatewayRoutes")
        return FunSpec.builder("resolveTarget")
            .addModifiers(com.squareup.kotlinpoet.KModifier.PRIVATE)
            .addParameter("route", String::class)
            .returns(routeTarget)
            .beginControlFlow("return when (route)")
            .addStatement("%T.GATEWAY_LOCAL -> %T.GatewayLocal", gatewayRoutes, routeTarget)
            .addStatement("%T.PLAYER -> %T.Entity(%T(%T.PlayerActor))", gatewayRoutes, routeTarget, entityKind, gameEntityKinds)
            .addStatement("%T.WORLD -> %T.Entity(%T(%T.WorldActor))", gatewayRoutes, routeTarget, entityKind, gameEntityKinds)
            .addStatement("else -> error(%P + route)", "unsupported gateway route: ")
            .endControlFlow()
            .build()
    }

    private fun buildResolveEntityIdHelper(): FunSpec {
        val routeSpecType =
            ClassName("com.mikai233.gate.generated", "GeneratedGatewayRouting", "GeneratedGatewayRouteSpec")
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
        val gatewayRouteSources = ClassName("com.mikai233.common.message", "GatewayRouteSources")
        return FunSpec.builder("resolveLongSource")
            .addModifiers(com.squareup.kotlinpoet.KModifier.PRIVATE)
            .addParameter("source", String::class)
            .addParameter("route", gatewayRoute)
            .addParameter("context", gatewaySessionContext)
            .addParameter("packet", clientProtobuf)
            .returns(Long::class)
            .beginControlFlow("return when")
            .addStatement(
                "source.startsWith(%T.MESSAGE_PREFIX) -> readMessageLongField(packet.message, source.removePrefix(%T.MESSAGE_PREFIX))",
                gatewayRouteSources,
                gatewayRouteSources,
            )
            .addStatement(
                "source == %T.SESSION_PLAYER_ID -> requireNotNull(context.session.get(com.mikai233.gate.GatePlayerIdKey))",
                gatewayRouteSources,
            )
            .addStatement(
                "source == %T.SESSION_WORLD_ID -> requireNotNull(context.session.get(com.mikai233.gate.GateWorldIdKey))",
                gatewayRouteSources,
            )
            .addStatement("source == %T.ROUTE_ENTITY_ID -> route.entityId as Long", gatewayRouteSources)
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

    private fun loadClientMessageIds(
        descriptorSet: DescriptorProtos.FileDescriptorSet,
    ): Map<String, Int> {
        val generatedTypes = discoverGeneratedTypes(descriptorSet)
        val file = descriptorSet.fileList.first { it.name == "client/msg_cs.proto" }
        val wrapper = file.messageTypeList.first { it.name == "MessageClientToServer" }
        return wrapper.fieldList.associate { field ->
            val protoFullName = field.typeName.removePrefix(".")
            requireNotNull(generatedTypes[protoFullName]) {
                "generated type for client message $protoFullName not found"
            } to field.number
        }
    }

    private fun defaultEntityId(route: String): String? {
        return when (route) {
            GATEWAY_LOCAL_ROUTE -> null
            PLAYER_ROUTE -> SESSION_PLAYER_ID_SOURCE
            WORLD_ROUTE -> MESSAGE_WORLD_ID_SOURCE
            else -> null
        }
    }

    private fun defaultInject(
        route: String,
        descriptor: DescriptorProtos.DescriptorProto?,
    ): List<String> {
        if (route != PLAYER_ROUTE) {
            return emptyList()
        }
        val hasPlayerId = descriptor?.fieldList?.any { field -> field.name == PLAYER_ID_FIELD } == true
        return if (hasPlayerId) {
            listOf(INJECT_PLAYER_ID_FROM_ROUTE_ENTITY_ID)
        } else {
            emptyList()
        }
    }

    private fun discoverGeneratedTypes(
        descriptorSet: DescriptorProtos.FileDescriptorSet,
    ): Map<String, String> {
        return buildMap {
            descriptorSet.fileList.forEach { file ->
                file.messageTypeList.forEach { message ->
                    val protoFullName = "${file.`package`}.${message.name}"
                    val generatedType = "${file.`package`}.${outerClassName(file.name)}.${message.name}"
                    put(protoFullName, generatedType)
                }
            }
        }
    }

    private fun discoverGeneratedMessageDescriptors(
        descriptorSet: DescriptorProtos.FileDescriptorSet,
    ): Map<String, DescriptorProtos.DescriptorProto> {
        return buildMap {
            descriptorSet.fileList.forEach { file ->
                file.messageTypeList.forEach { message ->
                    val generatedType = "${file.`package`}.${outerClassName(file.name)}.${message.name}"
                    put(generatedType, message)
                }
            }
        }
    }

    private fun outerClassName(protoFileName: String): String {
        val baseName = File(protoFileName).nameWithoutExtension
        return baseName.split('_')
            .filter { it.isNotBlank() }
            .joinToString("") { segment ->
                segment.replaceFirstChar { char -> char.uppercase() }
            }
    }
}

private fun com.fasterxml.jackson.databind.JsonNode.optionalText(fieldName: String): String? {
    return if (hasNonNull(fieldName)) path(fieldName).asText() else null
}

private fun com.fasterxml.jackson.databind.JsonNode.optionalTextList(fieldName: String): List<String>? {
    return if (hasNonNull(fieldName)) path(fieldName).map { it.asText() } else null
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
