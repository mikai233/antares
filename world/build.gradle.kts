plugins {
    groovy
    alias(libTool.plugins.ksp)
}

dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libTool.bundles.asteria.foundation)
    implementation(libTool.bundles.asteria.cluster)
    implementation(libTool.bundles.asteria.persistence)
    implementation(libTool.bundles.asteria.script)
    implementation(libPekko.bundles.common)
    implementation(libKotlinx.core)
    implementation(libKotlinx.reactor)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(libKotlin.reflect)
    implementation(libKotlinx.datetime.jvm)
    implementation(libTool.protobuf.java.util)
    implementation(libTool.protobuf.kotlin)
    implementation(libTool.jcommander)
    implementation(libTool.guava)
    implementation(libTool.spring.data.mongodb)
    implementation(project(":common"))
    ksp(project(":message-ksp"))
    implementation(project(":proto"))
}

tasks.test {
    useJUnitPlatform()
}
