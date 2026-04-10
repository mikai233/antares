dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libPekko.bundles.common)
//    implementation(libPekko.discovery)
    implementation(libPekko.management)
    implementation(libPekko.management.cluster.http)
    implementation(libKotlinx.core)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(libKtor.bundles.common)
    implementation(libTool.jcommander)
    implementation(libTool.kryo)
    implementation(libTool.bundles.curator)
    implementation(project(":common"))
}

tasks.test {
    useJUnitPlatform()
}
