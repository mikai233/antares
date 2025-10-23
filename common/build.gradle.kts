plugins {
    alias(libTool.plugins.ksp)
}

dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libAkka.bundles.common)
    implementation(libKotlin.bundles.common)
    implementation(libKotlinx.core)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(libKotlinx.datetime)
    implementation(libTool.bundles.curator)
    implementation(libLog.bundles.common)
    implementation(libTool.bundles.jackson)
    implementation(libTool.jackson.protobuf)
    implementation(libTool.reflections)
    implementation(libTool.protobuf.kotlin)
    implementation(libTool.protobuf.java.util)
    implementation(libTool.bcprov)
    implementation(libTool.groovy.all)
    implementation(libTool.guava)
    implementation(libTool.jcommander)
    implementation(libTool.easyexcel)
    implementation(libTool.spring.data.mongodb)
    implementation(libTool.spring.retry)
    implementation(libTool.mongodb.driver.sync)
    implementation(libTool.agrona)
    implementation(libTool.akka.kryo)
    implementation(libTool.caffeine)
    implementation(libTool.kryo)
    implementation(libTool.lz4)
    implementation(libTool.bundles.prometheus)
    implementation(project(":proto"))
    implementation(project(":processor"))
    ksp(project(":processor"))
}

tasks.test {
    useJUnitPlatform()
}
