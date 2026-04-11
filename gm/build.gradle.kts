dependencies {
    implementation(platform(libTool.spring.boot.dependencies))
    testImplementation(platform(libTool.spring.boot.dependencies))
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    testImplementation(libTool.spring.boot.starter.test)
    implementation(libPekko.bundles.common)
//    implementation(libPekko.discovery)
    implementation(libPekko.management)
    implementation(libPekko.management.cluster.http)
    implementation(libKotlinx.core)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(libTool.bundles.spring.boot.web)
    implementation(libTool.jcommander)
    implementation(libTool.kryo)
    implementation(libTool.bundles.curator)
    implementation(project(":common"))
}

tasks.test {
    useJUnitPlatform()
}
