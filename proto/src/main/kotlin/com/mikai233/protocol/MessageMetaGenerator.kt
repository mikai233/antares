package com.mikai233.protocol

import com.google.protobuf.Descriptors
import com.google.protobuf.GeneratedMessage
import com.google.protobuf.Parser
import com.mikai233.protocol.MsgCs.MessageClientToServer
import com.mikai233.protocol.MsgSc.MessageServerToClient
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.reflections.Reflections
import kotlin.io.path.Path
import kotlin.reflect.KClass

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/4
 */

internal typealias MessageClass = KClass<out GeneratedMessage>
internal typealias MessageMap = MutableMap<MessageClass, Int>

private const val MAX_ELEMENTS_PER_MAP = 1000

// KClass<out GeneratedMessage>
internal val MessageType =
    KClass::class.asTypeName().parameterizedBy(WildcardTypeName.producerOf(GeneratedMessage::class))

// Parser<out GeneratedMessage>
internal val MessageParserType =
    Parser::class.asTypeName().parameterizedBy(WildcardTypeName.producerOf(GeneratedMessage::class))

// Map<KClass<out GeneratedMessage>, Int>
internal val MessageMapType = MAP.parameterizedBy(MessageType, Int::class.asTypeName())

// Map<Int, Parser<out GeneratedMessage>
internal val ParserMapType = MAP.parameterizedBy(Int::class.asTypeName(), MessageParserType)

fun main() {
    val allMessages = scanAllMessages()
    val clientId = generateMessageMap(allMessages, MessageClientToServer.getDescriptor())
    val serverId = generateMessageMap(allMessages, MessageServerToClient.getDescriptor())
    val clientToServerFile = genMappingFile(clientId, MappingType.ClientToServer)
    val serverToClientFile = genMappingFile(serverId, MappingType.ServerToClient)
    clientToServerFile.writeTo(Path("src/main/kotlin"))
    serverToClientFile.writeTo(Path("src/main/kotlin"))
}

private fun scanAllMessages(): Map<String, MessageClass> {
    return Reflections("com.mikai233.protocol")
        .getSubTypesOf(GeneratedMessage::class.java)
        .associate { it.simpleName to it.kotlin }
}

private fun generateMessageMap(
    messages: Map<String, MessageClass>,
    descriptor: Descriptors.Descriptor,
): MessageMap {
    val idByMessageClass = mutableMapOf<MessageClass, Int>()
    descriptor.fields.forEach {
        val simpleName = it.messageType.name
        val fullName = it.messageType.fullName
        val number = it.number
        val messageClass = requireNotNull(messages[simpleName]) { "$fullName parser not found" }
        check(messageClass !in idByMessageClass) { "duplicate message:$fullName with number:$number" }
        idByMessageClass[messageClass] = number
    }
    return idByMessageClass
}

private fun genMappingFile(messageMap: MessageMap, type: MappingType): FileSpec {
    return FileSpec
        .builder("com.mikai233.protocol", type.name)
        .addProperties(genIdProperties(messageMap, type))
        .addProperties(genParserProperties(messageMap, type))
        .addType(genEnumSpec(messageMap, type))
        .addFunctions(generateHelperFunctions(type))
        .build()
}

private fun genIdProperties(messageMap: MessageMap, type: MappingType): List<PropertySpec> {
    val chunks = messageMap.entries.chunked(MAX_ELEMENTS_PER_MAP)
    val properties = mutableListOf<PropertySpec>()
    val chunkNames = mutableListOf<String>()
    chunks.forEachIndexed { index, chunk ->
        val chunkName = "${type.name}MessageById$index"
        chunkNames.add(chunkName)
        val propertySpec = PropertySpec.builder(chunkName, MessageMapType, KModifier.PRIVATE)
            .addKdoc("Automatically generated field, do not modify")
            .initializer(
                buildCodeBlock {
                    add("mapOf(\n")
                    chunk.forEachIndexed { index, entry ->
                        val (messageClass, id) = entry
                        add("%T::class to %L", messageClass, id)
                        if (index != messageMap.size - 1) {
                            add(",\n")
                        }
                    }
                    add("\n)")
                },
            )
            .build()
        properties.add(propertySpec)
    }
    val combinedProperty = PropertySpec
        .builder("${type.name}MessageById", MessageMapType)
        .addKdoc("Automatically generated field, do not modify")
        .initializer(
            buildCodeBlock {
                add("listOf(\n")
                chunkNames.forEach {
                    add("%L,", it)
                }
                add("\n)")
                add(".flatMap { it.entries.map { entry -> entry.key to entry.value } }.toMap()")
            },
        )
        .build()
    properties.add(combinedProperty)
    return properties
}

private fun genParserProperties(messageMap: MessageMap, type: MappingType): List<PropertySpec> {
    val chunks = messageMap.entries.chunked(MAX_ELEMENTS_PER_MAP)
    val properties = mutableListOf<PropertySpec>()
    val chunkNames = mutableListOf<String>()
    chunks.forEachIndexed { index, chunk ->
        val chunkName = "${type.name}ParserById$index"
        chunkNames.add(chunkName)
        val propertySpec = PropertySpec
            .builder(chunkName, ParserMapType, KModifier.PRIVATE)
            .addKdoc("Automatically generated field, do not modify")
            .initializer(
                buildCodeBlock {
                    add("mapOf(\n")
                    chunk.forEachIndexed { index, entry ->
                        val (messageClass, id) = entry
                        add("%L to %T.parser()", id, messageClass)
                        if (index != messageMap.size - 1) {
                            add(",\n")
                        }
                    }
                    add("\n)")
                },
            )
            .build()
        properties.add(propertySpec)
    }
    val combinedProperty = PropertySpec
        .builder("${type.name}ParserById", ParserMapType)
        .addKdoc("Automatically generated field, do not modify")
        .initializer(
            buildCodeBlock {
                add("listOf(\n")
                chunkNames.forEach {
                    add("%L,", it)
                }
                add("\n)")
                add(".flatMap { it.entries.map { entry -> entry.key to entry.value } }.toMap()")
            },
        )
        .build()
    properties.add(combinedProperty)
    return properties
}

private fun genEnumSpec(messageMap: MessageMap, type: MappingType): TypeSpec {
    val enumName = when (type) {
        MappingType.ClientToServer -> "CSEnum"
        MappingType.ServerToClient -> "SCEnum"
    }

    // 创建枚举项
    val enumSpec = TypeSpec.enumBuilder(enumName)
        .primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter("id", Int::class)
                .build(),
        )
        .addProperty(
            PropertySpec.builder("id", Int::class)
                .initializer("id")
                .build(),
        )
        .apply {
            messageMap.entries.sortedBy { it.value }.forEach { (messageClass, id) ->
                addEnumConstant(
                    requireNotNull(messageClass.simpleName) { "message $messageClass no simple name found" },
                    TypeSpec.anonymousClassBuilder().addSuperclassConstructorParameter("$id").build(),
                )
            }
        }

    // 添加伴生对象
    val companionObjectSpec = TypeSpec.companionObjectBuilder()
        .addProperty(
            PropertySpec.builder(
                "entriesById",
                MAP.parameterizedBy(Int::class.asTypeName(), ClassName("com.mikai233.protocol", enumName)),
            )
                .initializer("entries.associateBy { it.id }")
                .addModifiers(KModifier.PRIVATE)
                .build(),
        )
        .addFunction(
            FunSpec.builder("get")
                .addParameter("id", Int::class)
                .returns(ClassName("", enumName))
                .addStatement("return requireNotNull(entriesById[id]) { \"%L not found\" }", "\$id")
                .addModifiers(KModifier.OPERATOR)
                .build(),
        )
        .build()

    enumSpec.addType(companionObjectSpec)
    return enumSpec.build()
}

private fun generateHelperFunctions(type: MappingType): List<FunSpec> {
    val (from, to) = when (type) {
        MappingType.ClientToServer -> "Client" to "Server"
        MappingType.ServerToClient -> "Server" to "Client"
    }
    val messageById = "${from}To${to}MessageById"
    val parserById = "${from}To${to}ParserById"

    val idFun = FunSpec.builder("idFor${from}Message")
        .addParameter("messageKClass", MessageType)
        .returns(Int::class)
        .addCode(
            """
            return requireNotNull($messageById[messageKClass]) {
                "$from proto id for ${'$'}{messageKClass.qualifiedName} not found"
            }
            """.trimIndent(),
        )
        .build()

    val parserFun = FunSpec.builder("parserFor${from}Message")
        .addParameter("id", Int::class)
        .returns(MessageParserType)
        .addCode(
            """
            return requireNotNull($parserById[id]) {
                "parser for $from proto ${'$'}id not found"
            }
            """.trimIndent(),
        )
        .build()

    return listOf(idFun, parserFun)
}
