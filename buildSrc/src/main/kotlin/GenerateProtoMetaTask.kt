import com.google.protobuf.DescriptorProtos
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.MUTABLE_MAP
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.buildCodeBlock
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class GenerateProtoMetaTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val descriptorSetFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val descriptorSet = DescriptorProtos.FileDescriptorSet.parseFrom(descriptorSetFile.get().asFile.inputStream())
        val generatedTypes = discoverGeneratedTypes(descriptorSet)
        val clientMessages = discoverEnvelopeMessages(
            descriptorSet,
            generatedTypes,
            protoFileName = "client/msg_cs.proto",
            wrapperName = "MessageClientToServer",
        )
        val serverMessages = discoverEnvelopeMessages(
            descriptorSet,
            generatedTypes,
            protoFileName = "client/msg_sc.proto",
            wrapperName = "MessageServerToClient",
        )

        val target = outputDir.get().asFile
        if (target.exists()) {
            target.deleteRecursively()
        }
        target.mkdirs()

        genMappingFiles(clientMessages, MappingType.ClientToServer).forEach { it.writeTo(target.toPath()) }
        genMappingFiles(serverMessages, MappingType.ServerToClient).forEach { it.writeTo(target.toPath()) }
    }

    private fun discoverGeneratedTypes(
        descriptorSet: DescriptorProtos.FileDescriptorSet,
    ): Map<String, ClassName> {
        val result = linkedMapOf<String, ClassName>()
        descriptorSet.fileList.forEach { file ->
            val packageName = javaPackageName(file)
            val outerClassName = javaOuterClassName(file)
            val topLevelEnclosingClassNames = if (file.options.javaMultipleFiles) {
                emptyList()
            } else {
                listOf(outerClassName)
            }
            file.messageTypeList.forEach { message ->
                collectGeneratedTypes(
                    result,
                    filePackage = file.`package`,
                    javaPackage = packageName,
                    enclosingClassNames = topLevelEnclosingClassNames,
                    protoPath = listOf(message.name),
                    message = message,
                )
            }
        }
        return result
    }

    private fun collectGeneratedTypes(
        target: MutableMap<String, ClassName>,
        filePackage: String,
        javaPackage: String,
        enclosingClassNames: List<String>,
        protoPath: List<String>,
        message: DescriptorProtos.DescriptorProto,
    ) {
        val protoFullName = (listOf(filePackage) + protoPath).filter { it.isNotBlank() }.joinToString(".")
        target[protoFullName] = ClassName(javaPackage, enclosingClassNames + message.name)
        message.nestedTypeList.forEach { nested ->
            collectGeneratedTypes(
                target,
                filePackage,
                javaPackage,
                enclosingClassNames + message.name,
                protoPath + nested.name,
                nested,
            )
        }
    }

    private fun discoverEnvelopeMessages(
        descriptorSet: DescriptorProtos.FileDescriptorSet,
        generatedTypes: Map<String, ClassName>,
        protoFileName: String,
        wrapperName: String,
    ): List<MessageMeta> {
        val file = descriptorSet.fileList.firstOrNull { it.name == protoFileName }
            ?: error("protobuf descriptor file $protoFileName not found")
        val wrapper = file.messageTypeList.firstOrNull { it.name == wrapperName }
            ?: error("protobuf message $wrapperName not found in $protoFileName")
        val seenIds = mutableSetOf<Int>()
        val seenTypes = mutableSetOf<ClassName>()
        return wrapper.fieldList.map { field ->
            check(field.type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                "protobuf field ${field.name} in $wrapperName must be a message field"
            }
            check(seenIds.add(field.number)) {
                "duplicate protobuf id ${field.number} in $wrapperName"
            }
            val protoFullName = field.typeName.removePrefix(".")
            val className = requireNotNull(generatedTypes[protoFullName]) {
                "generated type for protobuf message $protoFullName not found"
            }
            check(seenTypes.add(className)) {
                "duplicate protobuf message $protoFullName in $wrapperName"
            }
            MessageMeta(
                id = field.number,
                simpleName = protoFullName.substringAfterLast('.'),
                className = className,
            )
        }
    }

    private fun javaPackageName(file: DescriptorProtos.FileDescriptorProto): String =
        file.options.takeIf { it.hasJavaPackage() }?.javaPackage ?: file.`package`

    private fun javaOuterClassName(file: DescriptorProtos.FileDescriptorProto): String =
        file.options.takeIf { it.hasJavaOuterClassname() }?.javaOuterClassname ?: outerClassName(file.name)

    private fun outerClassName(protoFileName: String): String {
        val baseName = File(protoFileName).nameWithoutExtension
        return baseName.split('_')
            .filter { it.isNotBlank() }
            .joinToString("") { segment ->
                segment.replaceFirstChar { char -> char.uppercase() }
            }
    }

    private fun genMappingFiles(messages: List<MessageMeta>, type: MappingType): List<FileSpec> {
        val chunks = messages.chunked(MESSAGES_PER_CHUNK)
        val chunkMetadata = chunks.mapIndexed { index, chunk ->
            genChunkFile(index, chunk, type)
        }
        val mainFile = FileSpec
            .builder("com.mikai233.protocol", type.name)
            .addType(genEnumSpec(messages, type))
            .addProperties(
                genRuntimeMapProperties(
                    type,
                    messages.size,
                    chunkMetadata.map { it.messageIdsFunction },
                    chunkMetadata.map { it.parsersByIdFunction },
                ),
            )
            .addFunctions(generateHelperFunctions(type, chunkMetadata.map { it.parsersByTypeFunction }))
            .build()
        return listOf(mainFile) + chunkMetadata.map { it.file }
    }

    private fun genChunkFile(index: Int, messages: List<MessageMeta>, type: MappingType): GeneratedChunkFile {
        val messageIdsFunction = "register${type.name}MessageIdsChunk$index"
        val parsersByIdFunction = "register${type.name}ParsersByIdChunk$index"
        val parsersByTypeFunction = "register${type.name}ParsersByTypeChunk$index"
        val file = FileSpec.builder("com.mikai233.protocol", "${type.name}Chunk$index")
            .addFunction(genMessageIdRegisterChunkFunction(messages, messageIdsFunction))
            .addFunction(genParserByIdRegisterChunkFunction(messages, parsersByIdFunction))
            .addFunction(genParserByTypeRegisterChunkFunction(messages, parsersByTypeFunction))
            .build()
        return GeneratedChunkFile(file, messageIdsFunction, parsersByIdFunction, parsersByTypeFunction)
    }

    private fun genMessageIdRegisterChunkFunction(messages: List<MessageMeta>, functionName: String): FunSpec {
        return FunSpec.builder(functionName)
            .addModifiers(KModifier.INTERNAL)
            .addKdoc("Automatically generated field, do not modify")
            .addParameter("target", MutableMessageIdByClassMapType)
            .addCode(
                buildCodeBlock {
                    messages.forEach { message ->
                        addStatement("target[%T::class.java] = %L", message.className, message.id)
                    }
                },
            )
            .build()
    }

    private fun genParserByIdRegisterChunkFunction(messages: List<MessageMeta>, functionName: String): FunSpec {
        return FunSpec.builder(functionName)
            .addModifiers(KModifier.INTERNAL)
            .addKdoc("Automatically generated field, do not modify")
            .addParameter("target", MutableParserByIdMapType)
            .addCode(
                buildCodeBlock {
                    messages.forEach { message ->
                        addStatement("target[%L] = %T.parser()", message.id, message.className)
                    }
                },
            )
            .build()
    }

    private fun genParserByTypeRegisterChunkFunction(messages: List<MessageMeta>, functionName: String): FunSpec {
        return FunSpec.builder(functionName)
            .addModifiers(KModifier.INTERNAL)
            .addKdoc("Automatically generated field, do not modify")
            .addParameter("target", MutableParserByTypeMapType)
            .addCode(
                buildCodeBlock {
                    messages.forEach { message ->
                        addStatement("target[%T::class.java] = %T.parser()", message.className, message.className)
                    }
                },
            )
            .build()
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

    private fun genEnumSpec(messages: List<MessageMeta>, type: MappingType): TypeSpec {
        val enumName = when (type) {
            MappingType.ClientToServer -> "CSEnum"
            MappingType.ServerToClient -> "SCEnum"
        }

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
                messages.sortedBy { it.id }.forEach { message ->
                    addEnumConstant(
                        message.simpleName,
                        TypeSpec.anonymousClassBuilder().addSuperclassConstructorParameter("%L", message.id).build(),
                    )
                }
            }

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

    private data class MessageMeta(
        val id: Int,
        val simpleName: String,
        val className: ClassName,
    )

    private data class GeneratedChunkFile(
        val file: FileSpec,
        val messageIdsFunction: String,
        val parsersByIdFunction: String,
        val parsersByTypeFunction: String,
    )

    private enum class MappingType {
        ClientToServer,
        ServerToClient,
    }

    private companion object {
        private const val MESSAGES_PER_CHUNK = 100

        private val GeneratedMessageClass = ClassName("com.google.protobuf", "GeneratedMessage")
        private val ParserClass = ClassName("com.google.protobuf", "Parser")
        private val KClassClass = ClassName("kotlin.reflect", "KClass")
        private val HashMapClass = ClassName("kotlin.collections", "HashMap")

        private val MessageType =
            KClassClass.parameterizedBy(WildcardTypeName.producerOf(GeneratedMessageClass))
        private val MessageParserType =
            ParserClass.parameterizedBy(WildcardTypeName.producerOf(GeneratedMessageClass))
        private val JavaMessageType =
            Class::class.asClassName().parameterizedBy(WildcardTypeName.producerOf(GeneratedMessageClass))
        private val MessageIdByClassMapType = MAP.parameterizedBy(JavaMessageType, Int::class.asTypeName())
        private val MutableMessageIdByClassMapType =
            MUTABLE_MAP.parameterizedBy(JavaMessageType, Int::class.asTypeName())
        private val ParserByIdMapType = MAP.parameterizedBy(Int::class.asTypeName(), MessageParserType)
        private val MutableParserByIdMapType = MUTABLE_MAP.parameterizedBy(Int::class.asTypeName(), MessageParserType)
        private val MutableParserByTypeMapType = MUTABLE_MAP.parameterizedBy(JavaMessageType, MessageParserType)
    }
}
