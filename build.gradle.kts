import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    alias(kt.plugins.jvm)
    alias(kt.plugins.allopen)
    alias(kt.plugins.noarg)
    alias(tool.plugins.detekt)
    alias(tool.plugins.dokka)
    alias(tool.plugins.boot) apply false
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

allprojects {
    group = Version.PROJECT_GROUP
    version = Version.PROJECT_VERSION

    afterEvaluate {
        configureJvmTarget()
    }
}

subprojects {
    apply(plugin = "kotlin")
    apply(plugin = "kotlin-allopen")
    apply(plugin = "kotlin-noarg")
    apply(plugin = "io.gitlab.arturbosch.detekt")
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

    if (project.name == "shared") {
        sourceSets.main {
            kotlin.srcDir("build/generated/ksp/main/kotlin")
        }
        sourceSets.test {
            kotlin.srcDir("build/generated/ksp/test/kotlin")
        }
    }

    sourceSets {
        create("script") {
            compileClasspath += main.get().run { compileClasspath + output }
        }
    }

    if (Boot.contains(project.name) || project.name == "common") {
        val scriptSourceSets = sourceSets["script"]
        scriptSourceSets.output.classesDirs.forEach { file ->
            val scriptClassesDir = file.resolve("com/mikai233/${project.name}/script")
            scriptClassesDir.walk().filter { it.isFile && it.extension == "class" }.forEach { classFile ->
                val className = classFile.nameWithoutExtension
                tasks.register<Jar>("buildJarFor${className}") {
                    group = "script"
                    description = "Build JAR for $className"
                    archiveFileName.set("${rootProject.name}_${project.name}_${className}.jar")
                    manifest {
                        attributes("Script-Class" to "com.mikai233.${project.name}.script.${className}")
                    }
                    from(scriptSourceSets.output)
                    include("com/mikai233/${project.name}/script/*")
                }
            }
        }
    }
    val actor = Forward[project.name]
    if (actor != null) {
        tasks.register<JavaExec>("generateProtoForwardMap") {
            group = "other"
            description = "Generates protobuf forward map for gate"
            mainClass = "com.mikai233.common.message.MessageForwardGeneratorKt"
            val sourceSetsMain = sourceSets.main.get()
            classpath = sourceSetsMain.runtimeClasspath
            val gateResourcesPath =
                project(":gate").extensions.getByType<SourceSetContainer>().main.get().resources.srcDirs.first().path
            args =
                listOf(
                    "-p",
                    "com.mikai233.${project.name}.handler",
                    "-o",
                    gateResourcesPath,
                    "-f",
                    actor
                )
        }
        tasks.named("compileKotlin") {
            finalizedBy("generateProtoForwardMap")
        }
    }
    tasks.register("generateVersionFile") {
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
    jvmToolchain(17)
}

fun Project.configureJvmTarget() {
    tasks.withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        with(options) {
            encoding = "UTF-8"
            isFork = true
        }
    }
    tasks.withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            javaParameters.set(true)
        }
    }
}

tasks.register("release") {
    val releaseDir: String by project
    group = "build"
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
