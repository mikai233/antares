dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libTool.kotlinpoet)
    implementation(libLog.bundles.common)
    implementation(libKotlinx.datetime)
    implementation(libKotlinx.core)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(libKotlin.reflect)
    implementation(libTool.bundles.curator)
    implementation(libTool.guava)
    implementation(libTool.lz4)
    implementation(libTool.reflections)
    implementation(libTool.easyexcel)
    implementation(libTool.jcommander)
    implementation(project(":common"))
}

tasks.register<JavaExec>("exportGameConfig") {
    val excelPath: String by project
    group = "other"
    args = listOf("-e", excelPath, "-v", Version.PROJECT_VERSION)
    mainClass = "com.mikai233.tools.excel.GameConfigExporterKt"
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.test {
    useJUnitPlatform()
}
