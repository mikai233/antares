dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(tool.symbol.processing.api)
    implementation(tool.kotlinpoet)
    implementation(tool.kotlinpoet.ksp)
}

tasks.test {
    useJUnitPlatform()
}