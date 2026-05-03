import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.DescriptorProtos
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class RpcProtocolRegistryTask : DefaultTask() {
    @get:InputFile
    abstract val descriptorSetFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val descriptorSet = DescriptorProtos.FileDescriptorSet.parseFrom(descriptorSetFile.get().asFile.inputStream())
        val existing = loadExisting(outputFile.get().asFile)
        val generatedTypes = discoverGeneratedTypes(descriptorSet)
        val clientTypes = discoverClientTypes(descriptorSet, generatedTypes)
        val rpcTypes = discoverRpcTypes(descriptorSet, generatedTypes)
        val discoveredTypes = LinkedHashSet<String>().apply {
            addAll(clientTypes)
            addAll(rpcTypes)
        }
        val resultIds = LinkedHashMap<String, Int>()

        val reusedExisting = existing.filterKeys { it in discoveredTypes }
        val duplicateIds = reusedExisting.values.groupingBy { it }.eachCount().filterValues { it > 1 }
        check(duplicateIds.isEmpty()) { "duplicate rpc protocol ids found in existing registry: $duplicateIds" }
        resultIds.putAll(reusedExisting)

        assignMissingIds(resultIds, clientTypes, reusedExisting, baseId = 100_001)
        assignMissingIds(resultIds, rpcTypes, reusedExisting, baseId = 110_001)

        val messages = resultIds.entries.sortedBy { it.value }
        writeOutput(outputFile.get().asFile, messages)
    }

    private fun discoverGeneratedTypes(
        descriptorSet: DescriptorProtos.FileDescriptorSet,
    ): Map<String, String> {
        val result = linkedMapOf<String, String>()
        descriptorSet.fileList.forEach { file ->
            file.messageTypeList.forEach { message ->
                val protoFullName = "${file.`package`}.${message.name}"
                val generatedType = "${file.`package`}.${outerClassName(file.name)}.${message.name}"
                result[protoFullName] = generatedType
            }
        }
        return result
    }

    private fun discoverClientTypes(
        descriptorSet: DescriptorProtos.FileDescriptorSet,
        generatedTypes: Map<String, String>,
    ): List<String> {
        val result = mutableListOf<String>()
        descriptorSet.fileList
            .filter { it.name == "client/msg_cs.proto" || it.name == "client/msg_sc.proto" }
            .sortedBy { it.name }
            .forEach { file ->
                file.messageTypeList.forEach { wrapper ->
                    wrapper.fieldList.forEach { field ->
                        val protoFullName = field.typeName.removePrefix(".")
                        result += requireNotNull(generatedTypes[protoFullName]) {
                            "generated type for client message $protoFullName not found"
                        }
                    }
                }
            }
        return result
    }

    private fun discoverRpcTypes(
        descriptorSet: DescriptorProtos.FileDescriptorSet,
        generatedTypes: Map<String, String>,
    ): List<String> {
        val result = mutableListOf<String>()
        descriptorSet.fileList
            .filter { it.name.startsWith("rpc/") }
            .sortedBy { it.name }
            .forEach { file ->
                file.messageTypeList.forEach { message ->
                    val protoFullName = "${file.`package`}.${message.name}"
                    result += requireNotNull(generatedTypes[protoFullName]) {
                        "generated type for rpc message $protoFullName not found"
                    }
                }
            }
        return result
    }

    private fun assignMissingIds(
        resultIds: MutableMap<String, Int>,
        orderedTypes: List<String>,
        existing: Map<String, Int>,
        baseId: Int,
    ) {
        var nextId = maxOf(baseId - 1, orderedTypes.mapNotNull { existing[it] }.maxOrNull() ?: (baseId - 1)) + 1
        orderedTypes.forEach { type ->
            if (type !in resultIds) {
                while (nextId in resultIds.values) {
                    nextId++
                }
                resultIds[type] = nextId++
            }
        }
    }

    private fun loadExisting(file: File): Map<String, Int> {
        if (!file.exists()) {
            return emptyMap()
        }
        val mapper = ObjectMapper()
        val root = mapper.readTree(file)
        val messages = root.path("messages")
        if (!messages.isArray) {
            return emptyMap()
        }
        return buildMap {
            messages.forEach { node ->
                val type = node.path("type").asText()
                val id = node.path("id").asInt()
                if (type.isNotBlank() && id != 0) {
                    put(type, id)
                }
            }
        }
    }

    private fun writeOutput(file: File, messages: List<Map.Entry<String, Int>>) {
        file.parentFile.mkdirs()
        val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
        val root: ObjectNode = mapper.createObjectNode()
        val messagesNode: ArrayNode = root.putArray("messages")
        messages.forEach { (type, id) ->
            messagesNode.addObject()
                .put("id", id)
                .put("type", type)
        }
        mapper.writeValue(file, root)
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
