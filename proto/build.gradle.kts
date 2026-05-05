import com.google.protobuf.gradle.id
import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libTool.plugins.protobuf)
    alias(libTool.plugins.asteria.protobuf.protocol.codegen)
}

protobuf {
    protoc {
        val version = libTool.versions.protobuf.get()
        artifact = "com.google.protobuf:protoc:$version"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                id("kotlin")
            }
            task.generateDescriptorSet = true
            task.descriptorSetOptions.path =
                layout.buildDirectory.file("descriptors/${task.name}.pb").get().asFile.absolutePath
            task.descriptorSetOptions.includeImports = true
        }
    }
}

tasks.withType<Detekt>().configureEach {
    exclude(
        "**/com/mikai233/protocol/ClientToServer*.kt",
        "**/com/mikai233/protocol/ServerToClient*.kt",
    )
}

dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libTool.protobuf.kotlin)
    implementation(libTool.asteria.protocol.protobuf)
    implementation(libTool.asteria.rpc.protobuf)
}

val protoMetaOutputDir = layout.buildDirectory.dir("generated/source/proto-meta/main/kotlin")

val generateProtoMeta = tasks.register<GenerateProtoMetaTask>("generateProtoMeta") {
    group = "code generation"
    description = "Generates client/server protobuf id and parser metadata from the protobuf descriptor set."
    descriptorSetFile.set(layout.buildDirectory.file("descriptors/generateProto.pb"))
    outputDir.set(protoMetaOutputDir)
    dependsOn("generateProto")
}

val generateRpcProtocolRegistry = tasks.register<RpcProtocolRegistryTask>("generateRpcProtocolRegistry") {
    group = "code generation"
    description = "Generates the centralized internal protobuf RPC id registry."
    descriptorSetFile.set(layout.buildDirectory.file("descriptors/generateProto.pb"))
    outputFile.set(layout.projectDirectory.file("protocol/rpc-protocol.json"))
    dependsOn("generateProto")
}

asteriaProtobufProtocol {
    packageName.set("com.mikai233.protocol")
    rpc {
        enabled.set(true)
        // Internal protobuf RPC ids are currently registered centrally in
        // proto/protocol/rpc-protocol.json. The file is generated from the
        // descriptor set by generateRpcProtocolRegistry, which keeps id
        // allocation out of individual proto messages and separates internal
        // RPC registration from client/gateway routing.
        metadataFile.set(layout.projectDirectory.file("protocol/rpc-protocol.json"))
        descriptorSetFile.set(layout.buildDirectory.file("descriptors/generateProto.pb"))
        packageName.set("com.mikai233.protocol.rpc")
        className.set("GeneratedRpcProtocol")
    }
}

kotlin {
    sourceSets.main {
        kotlin.srcDir(protoMetaOutputDir)
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateProtoMeta)
}

tasks.named("generateAsteriaRpcProtocol") {
    dependsOn(generateRpcProtocolRegistry)
}

tasks.test {
    useJUnitPlatform()
}
