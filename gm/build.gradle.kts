dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libAkka.bundles.common)
//    implementation(libAkka.discovery)
    implementation(libAkka.management)
    implementation(libAkka.management.cluster.http)
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
