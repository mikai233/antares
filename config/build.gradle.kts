plugins {
    idea
    alias(libs.plugins.ksp)
    alias(libs.plugins.asteria.config.codegen)
}

val lubanDataDirProvider = providers.gradleProperty("lubanDataDir")
    .orElse(providers.environmentVariable("LUBAN_DATA_DIR"))
    .orElse("config/luban/Datas")
    .map { rootDir.resolveProjectPath(it) }
val lubanToolRootProvider = providers.gradleProperty("lubanToolRoot")
    .orElse(providers.environmentVariable("LUBAN_TOOL_ROOT"))
    .map { rootDir.resolveProjectPath(it) }
val lubanBundleOutputDirProvider = providers.gradleProperty("lubanBundleOutputDir")
    .map { rootDir.resolveProjectPath(it) }
    .let { layout.dir(it) }
    .orElse(layout.buildDirectory.dir("generated/luban/bundles"))
val gameConfigVersionProvider = providers.gradleProperty("gameConfigVersion")
    .orElse(project.version.toString())

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

asteriaConfigCodegen {
    packageName.set("com.mikai233.config.luban")
    tablesObjectName.set("GameConfigTables")
    accessorClassName.set("GameConfigs")
    addDependencies.set(false)
    luban {
        enabled.set(true)
        metadataFile.set(layout.projectDirectory.file("src/generated/luban/asteria-config-tables.json"))
        packageName.set("com.mikai233.config.luban")
        fileName.set("GeneratedGameConfigMarkers")
    }
}

dependencies {
    testImplementation(platform(libs.test.junit.bom))
    testImplementation(libs.test.junit.jupiter)
    testRuntimeOnly(libs.test.junit.platform.launcher)
    implementation(libs.asteria.foundation.contribution)
    implementation(libs.bundles.asteria.config)
    implementation(libs.bundles.kotlin.common)
    implementation(libs.kotlinx.core)
    ksp(libs.asteria.foundation.contribution.ksp)
    ksp(libs.asteria.config.ksp)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("config.buildDir", layout.buildDirectory.get().asFile.absolutePath)
}

tasks.matching { it.name in setOf("kspKotlin", "compileKotlin", "classes") }.configureEach {
    mustRunAfter(exportLubanConfig, generateLubanBridge)
}

val exportLubanConfig by tasks.registering(Exec::class) {
    group = "luban"
    description = "Export Luban Java code and binary data from Excel workbooks."
    workingDir(rootDir)
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
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
    doFirst {
        val lubanToolRoot = lubanToolRootProvider.orNull
            ?: error("Luban tool root is not configured. Set lubanToolRoot in gradle.properties or LUBAN_TOOL_ROOT.")
        environment("LUBAN_DATA_DIR", lubanDataDirProvider.get().absolutePath)
        environment("LUBAN_TOOL_ROOT", lubanToolRoot.absolutePath)
    }
    inputs.dir(lubanDataDirProvider)
    inputs.property("lubanToolRoot", lubanToolRootProvider.map { it.absolutePath }.orElse(""))
    inputs.file(rootDir.resolve("config/luban/luban.conf"))
    inputs.file(rootDir.resolve("config/luban/luban_server.conf"))
    inputs.file(rootDir.resolve("config/luban/Datas/__tables_server_used.xlsx"))
    inputs.file(rootDir.resolve("config/luban/generate.sh"))
    inputs.file(rootDir.resolve("config/luban/generate.ps1"))
    inputs.file(rootDir.resolve("config/luban/scripts/generate_demo_excels.py"))
    outputs.dir(layout.projectDirectory.dir("src/generated/luban/java"))
    outputs.dir(layout.buildDirectory.dir("generated/luban/resources/luban"))
}

val generateLubanBridge by tasks.registering(GenerateLubanBridgeTask::class) {
    group = "luban"
    description = "Generate Kotlin Luban bridge sources and Asteria config table metadata from Luban Java outputs."
    dependsOn(exportLubanConfig)
    generatedJavaDir.set(layout.projectDirectory.dir("src/generated/luban/java"))
    generatedDataDir.set(layout.buildDirectory.dir("generated/luban/resources/luban"))
    outputDir.set(layout.projectDirectory.dir("src/generated/luban/kotlin/com/mikai233/config/luban"))
    metadataFile.set(layout.projectDirectory.file("src/generated/luban/asteria-config-tables.json"))
}

tasks.register("refreshLubanConfig") {
    group = "luban"
    description = "Regenerate Luban exports, bridge sources, and config table metadata."
    dependsOn(generateLubanBridge)
}

tasks.register<JavaExec>("validateLubanConfigTables") {
    group = "luban"
    description = "Validate exported Luban binary tables and custom config rules."
    dependsOn(exportLubanConfig, "classes")
    mainClass = "com.mikai233.config.luban.GameConfigValidationCliKt"
    classpath = sourceSets["main"].runtimeClasspath
    args("tables", layout.buildDirectory.dir("generated/luban/resources/luban").get().asFile.absolutePath)
    systemProperty("config.buildDir", layout.buildDirectory.get().asFile.absolutePath)
}

tasks.register<JavaExec>("validateLubanConfigQueries") {
    group = "luban"
    description = "Load exported Luban binaries and verify derived game config query components can be built."
    dependsOn("refreshLubanConfig", "classes")
    mainClass = "com.mikai233.config.luban.GameConfigValidationCliKt"
    classpath = sourceSets["main"].runtimeClasspath
    args("queries", layout.buildDirectory.dir("generated/luban/resources/luban").get().asFile.absolutePath)
    systemProperty("config.buildDir", layout.buildDirectory.get().asFile.absolutePath)
}

tasks.register<Zip>("packageLubanConfigBundle") {
    group = "luban"
    description = "Package generated Luban binary tables into a single server-consumable bundle."
    dependsOn(exportLubanConfig)
    val metadataFile = layout.buildDirectory.file("generated/luban/bundle-metadata/game-config.properties")
    inputs.property("gameConfigVersion", gameConfigVersionProvider)
    from(layout.buildDirectory.dir("generated/luban/resources/luban")) {
        include("*.bytes")
    }
    from(metadataFile) {
        into("META-INF/antares")
    }
    destinationDirectory.set(lubanBundleOutputDirProvider)
    archiveFileName.set("game-config.zip")
    doFirst {
        val version = gameConfigVersionProvider.get()
        require(version.isNotBlank()) { "gameConfigVersion must not be blank" }
        require('\n' !in version && '\r' !in version) { "gameConfigVersion must be single line" }
        val file = metadataFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText("version=$version\n")
    }
}

tasks.matching { it.name == "kspTestKotlin" }.configureEach {
    onlyIf {
        configurations.findByName("kspTest")?.allDependencies?.isNotEmpty() == true
    }
}

fun File.resolveProjectPath(path: String): File {
    val file = File(path)
    return if (file.isAbsolute) file else resolve(path)
}
