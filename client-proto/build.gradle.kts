import com.google.protobuf.gradle.id
import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.protobuf)
    alias(libs.plugins.asteria.protobuf.protocol.codegen)
}

protobuf {
    protoc {
        val version = libs.versions.protobuf.get()
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
    testImplementation(platform(libs.test.junit.bom))
    testImplementation(libs.test.junit.jupiter)
    implementation(libs.protobuf.kotlin)
    implementation(libs.asteria.protocol.protobuf)
    implementation(libs.asteria.rpc.protobuf)
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
    description = "Generates the client protobuf RPC id registry."
    descriptorSetFile.set(layout.buildDirectory.file("descriptors/generateProto.pb"))
    outputFile.set(layout.projectDirectory.file("protocol/rpc-protocol.json"))
    includeClientTypes.set(true)
    includeRpcTypes.set(false)
    dependsOn("generateProto")
}

asteriaProtobufProtocol {
    packageName.set("com.mikai233.protocol")
    rpc {
        enabled.set(true)
        metadataFile.set(layout.projectDirectory.file("protocol/rpc-protocol.json"))
        descriptorSetFile.set(layout.buildDirectory.file("descriptors/generateProto.pb"))
        packageName.set("com.mikai233.protocol.client")
        className.set("GeneratedClientProtocol")
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
