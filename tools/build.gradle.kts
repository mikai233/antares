dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libTool.bundles.asteria.cluster)
    implementation(libTool.bundles.asteria.config)
    implementation(libTool.bundles.asteria.persistence)
    implementation(libTool.kotlinpoet)
    implementation(libLog.bundles.common)
    implementation(libKotlinx.datetime)
    implementation(libKotlinx.core)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(libKotlin.reflect)
    implementation(libTool.bundles.curator)
    implementation(libTool.guava)
    implementation(libTool.jcommander)
    implementation(project(":common"))
    implementation(project(":gate"))
    implementation(project(":global"))
    implementation(project(":gm"))
    implementation(project(":player"))
    implementation(project(":world"))
}

tasks.test {
    useJUnitPlatform()
}
