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

private data class GeneratedChunkFunctions(val functions: List<FunSpec>, val chunkNames: List<String>)

private const val MAX_ELEMENTS_PER_MAP = 1000

// KClass<out GeneratedMessage>
internal val MessageType =
    KClass::class.asTypeName().parameterizedBy(WildcardTypeName.producerOf(GeneratedMessage::class))

// Parser<out GeneratedMessage>
internal val MessageParserType =
    Parser::class.asTypeName().parameterizedBy(WildcardTypeName.producerOf(GeneratedMessage::class))

internal val JavaMessageType =
    Class::class.asClassName().parameterizedBy(WildcardTypeName.producerOf(GeneratedMessage::class.asTypeName()))
internal val MessageIdByClassMapType = MAP.parameterizedBy(JavaMessageType, Int::class.asTypeName())
internal val MutableMessageIdByClassMapType = MUTABLE_MAP.parameterizedBy(JavaMessageType, Int::class.asTypeName())
internal val ParserByIdMapType = MAP.parameterizedBy(Int::class.asTypeName(), MessageParserType)
internal val MutableParserByIdMapType = MUTABLE_MAP.parameterizedBy(Int::class.asTypeName(), MessageParserType)
internal val MutableParserByTypeMapType = MUTABLE_MAP.parameterizedBy(JavaMessageType, MessageParserType)
private val HashMapClass = ClassName("kotlin.collections", "HashMap")

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
    val messagesByFullName = mutableMapOf<String, MessageClass>()
    Reflections("com.mikai233.protocol")
        .getSubTypesOf(GeneratedMessage::class.java)
        .forEach { messageClass ->
            val descriptor = messageClass.getMethod("getDescriptor").invoke(null) as Descriptors.Descriptor
            val fullName = descriptor.fullName
            check(fullName !in messagesByFullName) {
                "duplicate generated message class for proto full name:$fullName"
            }
            messagesByFullName[fullName] = messageClass.kotlin
        }
    return messagesByFullName
}

private fun generateMessageMap(
    messages: Map<String, MessageClass>,
    descriptor: Descriptors.Descriptor,
): MessageMap {
    val idByMessageClass = mutableMapOf<MessageClass, Int>()
    descriptor.fields.forEach {
        val fullName = it.messageType.fullName
        val number = it.number
        val messageClass = requireNotNull(messages[fullName]) { "$fullName parser not found" }
        check(messageClass !in idByMessageClass) { "duplicate message:$fullName with number:$number" }
        idByMessageClass[messageClass] = number
    }
    return idByMessageClass
}

private fun genMappingFile(messageMap: MessageMap, type: MappingType): FileSpec {
    val idChunkFunctions = genMessageIdRegisterChunkFunctions(messageMap, type)
    val parserByIdChunkFunctions = genParserByIdRegisterChunkFunctions(messageMap, type)
    val parserByTypeChunkFunctions = genParserByTypeRegisterChunkFunctions(messageMap, type)
    return FileSpec
        .builder("com.mikai233.protocol", type.name)
        .addType(genEnumSpec(messageMap, type))
        .addFunctions(idChunkFunctions.functions)
        .addFunctions(parserByIdChunkFunctions.functions)
        .addFunctions(parserByTypeChunkFunctions.functions)
        .addProperties(
            genRuntimeMapProperties(
                type,
                messageMap.size,
                idChunkFunctions.chunkNames,
                parserByIdChunkFunctions.chunkNames,
            ),
        )
        .addFunctions(
            generateHelperFunctions(
                type,
                parserByTypeChunkFunctions.chunkNames,
            ),
        )
        .build()
}

private fun genMessageIdRegisterChunkFunctions(messageMap: MessageMap, type: MappingType): GeneratedChunkFunctions {
    val chunks = messageMap.entries.chunked(MAX_ELEMENTS_PER_MAP)
    val functions = mutableListOf<FunSpec>()
    val chunkNames = mutableListOf<String>()
    chunks.forEachIndexed { index, chunk ->
        val chunkName = "register${type.name}MessageIdsChunk$index"
        chunkNames.add(chunkName)
        val functionSpec = FunSpec.builder(chunkName)
            .addModifiers(KModifier.PRIVATE)
            .addKdoc("Automatically generated field, do not modify")
            .addParameter("target", MutableMessageIdByClassMapType)
            .addCode(
                buildCodeBlock {
                    chunk.forEach { entry ->
                        val (messageClass, id) = entry
                        addStatement("target[%T::class.java] = %L", messageClass, id)
                    }
                },
            )
            .build()
        functions.add(functionSpec)
    }
    return GeneratedChunkFunctions(functions, chunkNames)
}

private fun genParserByIdRegisterChunkFunctions(messageMap: MessageMap, type: MappingType): GeneratedChunkFunctions {
    val chunks = messageMap.entries.chunked(MAX_ELEMENTS_PER_MAP)
    val functions = mutableListOf<FunSpec>()
    val chunkNames = mutableListOf<String>()
    chunks.forEachIndexed { index, chunk ->
        val chunkName = "register${type.name}ParsersByIdChunk$index"
        chunkNames.add(chunkName)
        val functionSpec = FunSpec.builder(chunkName)
            .addModifiers(KModifier.PRIVATE)
            .addKdoc("Automatically generated field, do not modify")
            .addParameter("target", MutableParserByIdMapType)
            .addCode(
                buildCodeBlock {
                    chunk.forEach { entry ->
                        val (messageClass, id) = entry
                        addStatement("target[%L] = %T.parser()", id, messageClass)
                    }
                },
            )
            .build()
        functions.add(functionSpec)
    }
    return GeneratedChunkFunctions(functions, chunkNames)
}

private fun genParserByTypeRegisterChunkFunctions(messageMap: MessageMap, type: MappingType): GeneratedChunkFunctions {
    val chunks = messageMap.entries.chunked(MAX_ELEMENTS_PER_MAP)
    val functions = mutableListOf<FunSpec>()
    val chunkNames = mutableListOf<String>()
    chunks.forEachIndexed { index, chunk ->
        val chunkName = "register${type.name}ParsersByTypeChunk$index"
        chunkNames.add(chunkName)
        val functionSpec = FunSpec.builder(chunkName)
            .addModifiers(KModifier.PRIVATE)
            .addKdoc("Automatically generated field, do not modify")
            .addParameter("target", MutableParserByTypeMapType)
            .addCode(
                buildCodeBlock {
                    chunk.forEach { entry ->
                        val (messageClass, _) = entry
                        addStatement("target[%T::class.java] = %T.parser()", messageClass, messageClass)
                    }
                },
            )
            .build()
        functions.add(functionSpec)
    }
    return GeneratedChunkFunctions(functions, chunkNames)
}

private fun genRuntimeMapProperties(
    type: MappingType,
    size: Int,
    idChunkNames: List<String>,
    parserByIdChunkNames: List<String>,
): List<PropertySpec> {
    val messageIdPropertyName = "${type.name}MessageIdByClass"
    val parserByIdPropertyName = "${type.name}ParserById"
    val messageIdHashMapType = HashMapClass.parameterizedBy(JavaMessageType, Int::class.asTypeName())
    val parserByIdHashMapType = HashMapClass.parameterizedBy(Int::class.asTypeName(), MessageParserType)
    val messageIdProperty = PropertySpec.builder(messageIdPropertyName, MessageIdByClassMapType, KModifier.PRIVATE)
        .addKdoc("Automatically generated field, do not modify")
        .initializer(
            buildCodeBlock {
                beginControlFlow("%T(%L).apply", messageIdHashMapType, size)
                idChunkNames.forEach { addStatement("%L(this)", it) }
                endControlFlow()
            },
        )
        .build()
    val parserByIdProperty = PropertySpec.builder(parserByIdPropertyName, ParserByIdMapType, KModifier.PRIVATE)
        .addKdoc("Automatically generated field, do not modify")
        .initializer(
            buildCodeBlock {
                beginControlFlow("%T(%L).apply", parserByIdHashMapType, size)
                parserByIdChunkNames.forEach { addStatement("%L(this)", it) }
                endControlFlow()
            },
        )
        .build()
    return listOf(messageIdProperty, parserByIdProperty)
}

private fun generateHelperFunctions(type: MappingType, parserByTypeChunkNames: List<String>): List<FunSpec> {
    val (from, to) = when (type) {
        MappingType.ClientToServer -> "Client" to "Server"
        MappingType.ServerToClient -> "Server" to "Client"
    }
    val messageIdByClass = "${type.name}MessageIdByClass"
    val parserById = "${type.name}ParserById"

    val idByClassFun = FunSpec.builder("idFor${from}Message")
        .addParameter("messageClass", JavaMessageType)
        .returns(Int::class)
        .addCode(
            """
            return requireNotNull($messageIdByClass[messageClass]) {
                "$from proto id for ${'$'}{messageClass.name} not found"
            }
            """.trimIndent(),
        )
        .build()

    val idByKClassFun = FunSpec.builder("idFor${from}Message")
        .addParameter("messageKClass", MessageType)
        .returns(Int::class)
        .addStatement("return idFor%LMessage(messageKClass.java)", from)
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

    val registerFun = FunSpec.builder("register${from}ParsersByType")
        .addParameter("target", MutableParserByTypeMapType)
        .addCode(
            buildCodeBlock {
                parserByTypeChunkNames.forEach { chunkName ->
                    addStatement("%L(target)", chunkName)
                }
            },
        )
        .build()

    return listOf(idByClassFun, idByKClassFun, parserFun, registerFun)
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
