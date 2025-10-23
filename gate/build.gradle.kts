plugins {
    groovy
}

dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libAkka.bundles.common)
    implementation(libKotlin.reflect)
    implementation(libKotlinx.core)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(libLog.bundles.common)
    implementation(libTool.netty)
    implementation(libTool.lz4)
    implementation(libTool.reflections)
    implementation(libTool.protobuf.kotlin)
    implementation(libTool.protobuf.java.util)
    implementation(libTool.bundles.curator)
    implementation(libTool.jcommander)
    implementation(project(":common"))
    implementation(project(":proto"))
}

tasks.test {
    useJUnitPlatform()
}
