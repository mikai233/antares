plugins {
    groovy
    alias(libTool.plugins.ksp)
    alias(libTool.plugins.asteria.message.codegen)
}

asteriaMessageCodegen {
    dispatcherSuperType("PROTOBUF", "com.google.protobuf.GeneratedMessage")
    dispatcherSuperType("INTERNAL", "com.mikai233.common.message.Message")
}

ksp {
    arg("antares.config.package", "com.mikai233.player.generated")
    arg("antares.config.class", "GeneratedPlayerConfigChangeHandlers")
    arg("antares.config.actorType", "com.mikai233.player.PlayerActor")
}

dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libTool.bundles.asteria.foundation)
    implementation(libTool.bundles.asteria.cluster)
    implementation(libTool.bundles.asteria.persistence)
    implementation(libTool.bundles.asteria.script)
    implementation(libPekko.bundles.common)
    implementation(libKotlin.reflect)
    implementation(libKotlinx.core)
    implementation(libKotlinx.reactor)
    implementation(libKotlinx.datetime.jvm)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(libTool.protobuf.kotlin)
    implementation(libTool.groovy.all)
    implementation(libTool.jcommander)
    implementation(libTool.spring.data.mongodb)
    implementation(project(":common"))
    implementation(project(":proto"))
    ksp(project(":message-ksp"))
}

tasks.test {
    useJUnitPlatform()
}
