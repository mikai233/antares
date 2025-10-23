dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(tool.kotlinpoet)
    implementation(log.bundles.common)
    implementation(libKotlinx.datetime)
    implementation(libKotlinx.core)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(libKotlin.reflect)
    implementation(tool.bundles.curator)
    implementation(tool.guava)
    implementation(tool.lz4)
    implementation(tool.reflections)
    implementation(tool.easyexcel)
    implementation(tool.jcommander)
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
