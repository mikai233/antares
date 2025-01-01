group = Version.PROJECT_GROUP
version = Version.PROJECT_VERSION

dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(tool.symbol.processing.api)
    implementation(tool.kotlinpoet)
    implementation(tool.kotlinpoet.ksp)
    implementation(project(":common"))
}

tasks.test {
    useJUnitPlatform()
}