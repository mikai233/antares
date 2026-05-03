plugins {
    alias(libTool.plugins.ksp)
}

dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libTool.bundles.asteria.foundation)
    implementation(libTool.bundles.asteria.cluster)
    implementation(libTool.bundles.asteria.config)
    implementation(libTool.bundles.asteria.persistence)
    implementation(libTool.bundles.asteria.script)
    implementation(libPekko.bundles.common)
    implementation(libKotlin.bundles.common)
    implementation(libKotlinx.core)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(libKotlinx.datetime)
    implementation(libTool.bundles.curator)
    implementation(libLog.bundles.common)
    implementation(libTool.bundles.jackson)
    implementation(libTool.jackson.protobuf)
    testImplementation(libTool.reflections)
    implementation(libTool.protobuf.kotlin)
    implementation(libTool.protobuf.java.util)
    implementation(libTool.bcprov)
    implementation(libTool.groovy.all)
    implementation(libTool.guava)
    implementation(libTool.jcommander)
    implementation(libTool.spring.data.mongodb)
    implementation(libTool.spring.retry)
    implementation(libTool.mongodb.driver.sync)
    implementation(libTool.caffeine)
    implementation(libTool.lz4)
    implementation(libTool.netty)
    implementation(libTool.bundles.prometheus)
    implementation(project(":proto"))
    ksp(libTool.asteria.persistence.mongodb.ksp)
}

tasks.test {
    useJUnitPlatform()
}
