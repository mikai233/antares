dependencies {
    implementation(libKotlin.reflect)
    implementation(libTool.symbol.processing.api)
    implementation(libTool.kotlinpoet)
    implementation(libTool.kotlinpoet.ksp)
    implementation(project(":common"))
}
