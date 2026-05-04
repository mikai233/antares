plugins {
    groovy
    id("io.github.realm-labs.asteria.message-codegen")
}

asteriaMessageCodegen {
    dispatcherSuperType("PROTOBUF", "com.google.protobuf.GeneratedMessage")
    dispatcherSuperType("INTERNAL", "com.mikai233.common.message.Message")
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
    implementation(libTool.guava)
    implementation(libTool.spring.data.mongodb)
    implementation(project(":common"))
    implementation(project(":proto"))
}

tasks.test {
    useJUnitPlatform()
}
