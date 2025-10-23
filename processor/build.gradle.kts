dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(tool.symbol.processing.api)
    implementation(tool.kotlinpoet)
    implementation(tool.kotlinpoet.ksp)
}

tasks.test {
    useJUnitPlatform()
}
