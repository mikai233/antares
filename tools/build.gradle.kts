dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(tool.kotlinpoet)
    implementation(log.bundles.common)
    implementation(ktx.datetime)
    implementation(ktx.core)
    runtimeOnly(ktx.core.jvm)
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
