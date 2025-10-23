dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libTool.symbol.processing.api)
    implementation(libTool.kotlinpoet)
    implementation(libTool.kotlinpoet.ksp)
}

tasks.test {
    useJUnitPlatform()
}
