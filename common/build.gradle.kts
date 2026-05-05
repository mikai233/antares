plugins {
    idea
    alias(libTool.plugins.ksp)
}

val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
val lubanDataDirProvider = providers.gradleProperty("lubanDataDir")
    .map { rootDir.resolve(it) }
    .orElse(rootDir.resolve("config/luban/Datas"))
val lubanBundleOutputDirProvider = providers.gradleProperty("lubanBundleOutputDir")
    .map { layout.projectDirectory.dir(it) }
    .orElse(layout.buildDirectory.dir("generated/luban/bundles"))

kotlin {
    sourceSets.main {
        kotlin.srcDir("src/generated/luban/kotlin")
    }
}

java {
    sourceSets.main {
        java.srcDir("src/generated/luban/java")
    }
}

idea {
    module {
        generatedSourceDirs.add(file("src/generated/luban/java"))
        generatedSourceDirs.add(file("src/generated/luban/kotlin"))
    }
}

// Keep Luban binary exports out of the runtime classpath; runtime loads config through the
// project's configured publication/fetch flow rather than source-tree artifacts.

dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libTool.bundles.asteria.foundation)
    implementation(libTool.bundles.asteria.cluster)
    implementation(libTool.bundles.asteria.config)
    implementation(libTool.bundles.asteria.persistence)
    implementation(libTool.bundles.asteria.script)
    implementation(libPekko.bundles.common)
    implementation(libKotlin.bundles.common)
    implementation(libKotlinx.core)
    implementation(libKotlinx.reactor)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(libKotlinx.datetime)
    implementation(libTool.bundles.curator)
    implementation(libLog.bundles.common)
    implementation(libTool.bundles.jackson)
    implementation(libTool.jackson.protobuf)
    testImplementation(libTool.reflections)
    testImplementation(libTool.spring.data.mongodb)
    testImplementation(libTool.testcontainers.mongodb)
    implementation(libTool.protobuf.kotlin)
    implementation(libTool.protobuf.java.util)
    implementation(libTool.bcprov)
    implementation(libTool.groovy.all)
    implementation(libTool.guava)
    compileOnly(libTool.spring.data.commons)
    implementation(libTool.spring.data.mongodb)
    implementation(libTool.lz4)
    implementation(libTool.netty)
    implementation(libTool.bundles.prometheus)
    implementation(project(":proto"))
    ksp(libTool.asteria.persistence.mongodb.ksp)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("common.buildDir", layout.buildDirectory.get().asFile.absolutePath)
}

tasks.matching { it.name in setOf("kspKotlin", "compileKotlin", "classes") }.configureEach {
    mustRunAfter(exportLubanConfig, generateLubanBridge)
}

val exportLubanConfig by tasks.registering(Exec::class) {
    group = "luban"
    description = "Export Luban Java code and binary data from Excel workbooks."
    workingDir(rootDir)
    if (isWindows) {
        commandLine(
            "powershell",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            "${rootDir}/config/luban/generate.ps1",
        )
    } else {
        commandLine("bash", "${rootDir}/config/luban/generate.sh")
    }
    environment("LUBAN_DATA_DIR", lubanDataDirProvider.get().absolutePath)
    inputs.dir(lubanDataDirProvider)
    inputs.file(rootDir.resolve("config/luban/luban.conf"))
    inputs.file(rootDir.resolve("config/luban/generate.sh"))
    inputs.file(rootDir.resolve("config/luban/generate.ps1"))
    inputs.file(rootDir.resolve("config/luban/scripts/generate_demo_excels.py"))
    outputs.dir(layout.projectDirectory.dir("src/generated/luban/java"))
    outputs.dir(layout.buildDirectory.dir("generated/luban/resources/luban"))
}

val generateLubanBridge by tasks.registering(GenerateLubanBridgeTask::class) {
    group = "luban"
    description = "Generate Kotlin table adapters and Luban artifact metadata from Luban Java outputs."
    dependsOn(exportLubanConfig)
    generatedJavaDir.set(layout.projectDirectory.dir("src/generated/luban/java"))
    generatedDataDir.set(layout.buildDirectory.dir("generated/luban/resources/luban"))
    outputDir.set(layout.projectDirectory.dir("src/generated/luban/kotlin/com/mikai233/common/config/luban"))
}

tasks.register("refreshLubanConfig") {
    group = "luban"
    description = "Regenerate Luban exports and project-side table adapters."
    // Keep Luban export/bridge generation off the normal compile path; config schema changes are infrequent.
    dependsOn(generateLubanBridge)
}

tasks.register<JavaExec>("validateLubanConfigTables") {
    group = "luban"
    description = "Validate exported Luban binary tables and custom config rules."
    dependsOn(exportLubanConfig, "classes")
    mainClass = "com.mikai233.common.config.luban.GameConfigValidationCliKt"
    classpath = sourceSets["main"].runtimeClasspath
    args("tables", layout.buildDirectory.dir("generated/luban/resources/luban").get().asFile.absolutePath)
    systemProperty("common.buildDir", layout.buildDirectory.get().asFile.absolutePath)
}

tasks.register<JavaExec>("validateLubanConfigQueries") {
    group = "luban"
    description = "Load exported Luban binaries and verify derived GameConfigQueries can be built."
    dependsOn("refreshLubanConfig", "classes")
    mainClass = "com.mikai233.common.config.luban.GameConfigValidationCliKt"
    classpath = sourceSets["main"].runtimeClasspath
    args("queries", layout.buildDirectory.dir("generated/luban/resources/luban").get().asFile.absolutePath)
    systemProperty("common.buildDir", layout.buildDirectory.get().asFile.absolutePath)
}

tasks.register<Zip>("packageLubanConfigBundle") {
    group = "luban"
    description = "Package generated Luban binary tables into a single server-consumable bundle."
    dependsOn(exportLubanConfig)
    from(layout.buildDirectory.dir("generated/luban/resources/luban")) {
        include("*.bytes")
    }
    destinationDirectory.set(lubanBundleOutputDirProvider)
    archiveFileName.set("game-config.zip")
}

// `common` does not declare any test-side symbol processors. Keep `kspTestKotlin` dormant unless
// a future change adds real `kspTest(...)` dependencies; this avoids KSP/Gradle test snapshot churn.
tasks.matching { it.name == "kspTestKotlin" }.configureEach {
    onlyIf {
        configurations.findByName("kspTest")?.allDependencies?.isNotEmpty() == true
    }
}
