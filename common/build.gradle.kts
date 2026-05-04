plugins {
    alias(libTool.plugins.ksp)
}

sourceSets.main {
    java.srcDir("src/generated/luban/java")
    kotlin.srcDir("src/generated/luban/kotlin")
    // Keep Luban binary exports out of the runtime classpath; runtime loads config through the
    // project's configured publication/fetch flow rather than source-tree artifacts.
}

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
    implementation(libTool.jcommander)
    implementation(libTool.spring.retry)
    compileOnly(libTool.spring.data.commons)
    implementation(libTool.spring.data.mongodb)
    implementation(libTool.caffeine)
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

val exportLubanConfig by tasks.registering(Exec::class) {
    group = "luban"
    description = "Export Luban Java code and binary data from Excel workbooks."
    workingDir(rootDir)
    commandLine("bash", "${rootDir}/config/luban/generate.sh")
    inputs.dir(rootDir.resolve("config/luban/Datas"))
    inputs.file(rootDir.resolve("config/luban/luban.conf"))
    inputs.file(rootDir.resolve("config/luban/generate.sh"))
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

// `common` does not declare any test-side symbol processors. Keep `kspTestKotlin` dormant unless
// a future change adds real `kspTest(...)` dependencies; this avoids KSP/Gradle test snapshot churn.
tasks.matching { it.name == "kspTestKotlin" }.configureEach {
    onlyIf {
        configurations.findByName("kspTest")?.allDependencies?.isNotEmpty() == true
    }
}
