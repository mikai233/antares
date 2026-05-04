plugins {
    groovy
    id("io.github.realm-labs.asteria.message-codegen")
}

val generateGatewayRoutingTable by tasks.registering(GenerateGatewayRoutingTask::class) {
    dependsOn(":gate:kspKotlin", ":player:kspKotlin", ":world:kspKotlin")
    metadataFiles.from(
        project(":gate").layout.buildDirectory.file("generated/ksp/main/resources/META-INF/antares/gateway-route-hints/gate.json"),
        project(":player").layout.buildDirectory.file("generated/ksp/main/resources/META-INF/antares/gateway-route-hints/player.json"),
        project(":world").layout.buildDirectory.file("generated/ksp/main/resources/META-INF/antares/gateway-route-hints/world.json"),
    )
    outputDir.set(layout.buildDirectory.dir("generated/source/gateway/main/kotlin"))
}

kotlin {
    sourceSets.main {
        kotlin.srcDir(layout.buildDirectory.dir("generated/source/gateway/main/kotlin"))
    }
}

asteriaMessageCodegen {
    dispatcherSuperType("PROTOBUF", "com.google.protobuf.GeneratedMessage")
}

dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libTool.bundles.asteria.foundation)
    implementation(libTool.bundles.asteria.cluster)
    implementation(libTool.bundles.asteria.script)
    implementation(libTool.asteria.gateway.core)
    implementation(libTool.asteria.gateway.pekko)
    implementation(libTool.asteria.gateway.netty)
    implementation(libPekko.bundles.common)
    implementation(libKotlin.reflect)
    implementation(libKotlinx.core)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(libLog.bundles.common)
    implementation(libTool.netty)
    implementation(libTool.protobuf.kotlin)
    implementation(libTool.protobuf.java.util)
    implementation(libTool.bundles.curator)
    implementation(libTool.jcommander)
    implementation(project(":common"))
    implementation(project(":proto"))
    ksp(project(":message-ksp"))
}

tasks.compileKotlin {
    dependsOn(generateGatewayRoutingTable)
}

tasks.test {
    useJUnitPlatform()
}
