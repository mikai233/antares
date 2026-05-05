plugins {
    groovy
}

dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libTool.bundles.asteria.foundation)
    implementation(libTool.bundles.asteria.cluster)
    implementation(libTool.bundles.asteria.script)
    implementation(libPekko.bundles.common)
    implementation(libKotlinx.core)
    implementation(libKotlinx.datetime.jvm)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(libTool.protobuf.kotlin)
    implementation(libTool.groovy.all)
    implementation(libTool.jcommander)
    implementation(project(":common"))
    implementation(project(":proto"))
}

tasks.test {
    useJUnitPlatform()
}
