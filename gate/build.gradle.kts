plugins {
    groovy
    alias(libs.plugins.ksp)
    alias(libs.plugins.asteria.message.codegen)
}

val generateGatewayRoutingTable by tasks.registering(GenerateGatewayRoutingTask::class) {
    group = "code generation"
    description = "Generate gateway routing source from Asteria gateway route metadata."
    dependsOn(":proto:generateProto", ":gate:kspKotlin", ":player:kspKotlin", ":world:kspKotlin")
    metadataFiles.from(
        project(":gate").layout.buildDirectory.file("generated/ksp/main/resources/META-INF/asteria/gateway-route-hints/gate.json"),
        project(":player").layout.buildDirectory.file("generated/ksp/main/resources/META-INF/asteria/gateway-route-hints/player.json"),
        project(":world").layout.buildDirectory.file("generated/ksp/main/resources/META-INF/asteria/gateway-route-hints/world.json"),
    )
    descriptorSetFile.set(project(":proto").layout.buildDirectory.file("descriptors/generateProto.pb"))
    outputDir.set(layout.buildDirectory.dir("generated/source/gateway/main/kotlin"))
}

kotlin {
    sourceSets.main {
        kotlin.srcDir(layout.buildDirectory.dir("generated/source/gateway/main/kotlin"))
    }
}

asteriaMessageCodegen {
    generatedPackage.set("com.mikai233.gate.generated")
    moduleId.set("gate")
    dispatcherSuperType("PROTOBUF", "com.google.protobuf.GeneratedMessage")
}

dependencies {
    testImplementation(platform(libs.test.junit.bom))
    testImplementation(libs.test.junit.jupiter)
    implementation(libs.bundles.asteria.foundation)
    implementation(libs.bundles.asteria.cluster)
    implementation(libs.bundles.asteria.script)
    implementation(libs.asteria.gateway.core)
    implementation(libs.asteria.gateway.pekko)
    implementation(libs.asteria.gateway.netty)
    implementation(libs.bundles.pekko.common)
    implementation(libs.kotlinx.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.bundles.log.common)
    implementation(libs.netty)
    implementation(libs.lz4)
    implementation(libs.bcprov)
    implementation(libs.protobuf.kotlin)
    implementation(libs.jcommander)
    implementation(project(":common"))
    implementation(project(":proto"))
}

tasks.compileKotlin {
    dependsOn(generateGatewayRoutingTable)
}

tasks.test {
    useJUnitPlatform()
}
