plugins {
    alias(tool.plugins.ksp)
}

dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libAkka.bundles.common)
    implementation(libKotlin.bundles.common)
    implementation(libKotlinx.core)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(libKotlinx.datetime)
    implementation(tool.bundles.curator)
    implementation(log.bundles.common)
    implementation(tool.bundles.jackson)
    implementation(tool.jackson.protobuf)
    implementation(tool.reflections)
    implementation(tool.protobuf.kotlin)
    implementation(tool.protobuf.java.util)
    implementation(tool.bcprov)
    implementation(tool.groovy.all)
    implementation(tool.guava)
    implementation(tool.jcommander)
    implementation(tool.easyexcel)
    implementation(tool.spring.data.mongodb)
    implementation(tool.spring.retry)
    implementation(tool.mongodb.driver.sync)
    implementation(tool.agrona)
    implementation(tool.akka.kryo)
    implementation(tool.caffeine)
    implementation(tool.kryo)
    implementation(tool.lz4)
    implementation(tool.bundles.prometheus)
    implementation(project(":proto"))
    implementation(project(":processor"))
    ksp(project(":processor"))
}

tasks.test {
    useJUnitPlatform()
}
