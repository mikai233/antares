plugins {
    groovy
}

dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation("io.github.realm-labs.asteria:gateway-core:1.0-SNAPSHOT")
    implementation("io.github.realm-labs.asteria:gateway-pekko:1.0-SNAPSHOT")
    implementation("io.github.realm-labs.asteria:gateway-netty:1.0-SNAPSHOT")
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
}

tasks.test {
    useJUnitPlatform()
}
