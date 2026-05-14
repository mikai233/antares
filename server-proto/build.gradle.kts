import com.google.protobuf.gradle.id

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

dependencies {
    testImplementation(platform(libs.test.junit.bom))
    testImplementation(libs.test.junit.jupiter)
    implementation(libs.protobuf.kotlin)
    implementation(libs.asteria.protocol.protobuf)
    implementation(libs.asteria.rpc.protobuf)
}

val generateRpcProtocolRegistry = tasks.register<RpcProtocolRegistryTask>("generateRpcProtocolRegistry") {
    group = "code generation"
    description = "Generates the centralized internal protobuf RPC id registry."
    descriptorSetFile.set(layout.buildDirectory.file("descriptors/generateProto.pb"))
    outputFile.set(layout.projectDirectory.file("protocol/rpc-protocol.json"))
    includeClientTypes.set(false)
    includeRpcTypes.set(true)
    dependsOn("generateProto")
}

asteriaProtobufProtocol {
    packageName.set("com.mikai233.protocol")
    rpc {
        enabled.set(true)
        // Internal protobuf RPC ids are currently registered centrally in
        // server-proto/protocol/rpc-protocol.json. The file is generated from the
        // merged client/server descriptor set by generateRpcProtocolRegistry,
        // which keeps id allocation out of individual proto messages and
        // separates internal RPC registration from client/gateway routing.
        metadataFile.set(layout.projectDirectory.file("protocol/rpc-protocol.json"))
        descriptorSetFile.set(layout.buildDirectory.file("descriptors/generateProto.pb"))
        packageName.set("com.mikai233.protocol.rpc")
        className.set("GeneratedRpcProtocol")
    }
}

tasks.named("generateAsteriaRpcProtocol") {
    dependsOn(generateRpcProtocolRegistry)
}

tasks.test {
    useJUnitPlatform()
}
