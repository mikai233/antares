import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlin.noarg)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.boot) apply false
    alias(libs.plugins.version.catalog.update)
    id("antares-script-conventions") apply false
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

val unifiedProjectGroup = providers.gradleProperty("projectGroup").get()
val unifiedProjectVersion = providers.gradleProperty("projectVersion").get()

allprojects {
    group = unifiedProjectGroup
    version = unifiedProjectVersion

    apply(plugin = "io.gitlab.arturbosch.detekt")

    afterEvaluate {
        configureJvmTarget()
    }
}

subprojects {
    apply(plugin = "kotlin")
    apply(plugin = "kotlin-allopen")
    apply(plugin = "kotlin-noarg")
    apply(plugin = "org.jetbrains.dokka")
    if (Boot.contains(project.name)) {
        apply(plugin = "org.springframework.boot")
    }

    allOpen {
        annotation("com.mikai233.common.annotation.AllOpen")
    }
    noArg {
        invokeInitializers = true
        annotation("com.mikai233.common.annotation.NoArg")
    }

    if (project.name == "proto") {
        kotlin {
            sourceSets.main {
                kotlin.srcDir("build/generated/source/proto/main/kotlin")
                kotlin.srcDir("src/main/proto")
            }
        }
        java {
            sourceSets.main {
                java.srcDir("build/generated/source/proto/main/java")
            }
        }
    }

    if (project.name in setOf("common", "gate", "player", "world")) {
        sourceSets.main {
            kotlin.srcDir("build/generated/ksp/main/kotlin")
        }
    }
    if (Boot.contains(project.name) || project.name == "common") {
        apply(plugin = "antares-script-conventions")
    }
    tasks.register("generateVersionFile") {
        group = "version"
        description = "Generate a version resource file for ${project.path}."

        val resourcesDir = file("${layout.buildDirectory.get()}/generated/resources/")
        val versionFile = File(resourcesDir, "version")

        inputs.property("version", project.version.toString())
        outputs.file(versionFile)
        doLast {
            if (!resourcesDir.exists()) {
                resourcesDir.mkdirs()
            }
            versionFile.writeText("${project.version}")
        }
    }
    tasks.named<ProcessResources>("processResources") {
        from("${layout.buildDirectory.get()}/generated/resources") {
            include("version")
        }
    }
    tasks.named("processResources") {
        dependsOn("generateVersionFile")
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

fun Project.configureJvmTarget() {
    tasks.withType<JavaCompile>().configureEach {
        javaCompiler.set(
            javaToolchains.compilerFor {
                languageVersion.set(JavaLanguageVersion.of(21))
            },
        )
        sourceCompatibility = "21"
        targetCompatibility = "21"
        options.release.set(21)
        with(options) {
            encoding = "UTF-8"
            isFork = true
        }
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            javaParameters.set(true)
        }
    }
    tasks.withType<JavaExec>().configureEach {
        javaLauncher.set(
            javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
            },
        )
    }
}

tasks.register("release") {
    val releaseDir: String by project
    group = "build"
    description = "Collect executable node JARs into the configured release directory."
    dependsOn(getTasksByName("bootJar", true))
    doLast {
        Boot.forEach {
            copy {
                from("${project(it).layout.buildDirectory.get()}/libs") {
                    exclude { file -> file.name.endsWith("-plain.jar") }
                }
                into("$releaseDir/$it")
            }
        }
    }
}

tasks.getByName<Delete>("clean") {
    val releaseDir: String by project
    delete.add(releaseDir)
}

versionCatalogUpdate {
    catalogFile = file("gradle/libs.versions.toml")
}

tasks.register("printProjectVersion") {
    group = "version"
    description = "Print the unified project version used by code, config publication, and image tagging."
    doLast {
        println(version)
    }
}

gradle.projectsEvaluated {
    allprojects {
        tasks.configureEach {
            if (description.isNullOrBlank()) {
                description = "Runs the ${path} task."
            }
        }
    }
}
