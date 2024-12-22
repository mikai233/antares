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

    tasks.register<Jar>("buildKotlinScript") {
        group = "script"
        val scriptClass: String? by project
        val classSimpleName = scriptClass?.split(".")?.last()
        archiveFileName.set("${rootProject.name}_${project.name}_${classSimpleName}.jar")
        val script = sourceSets["script"]
        manifest {
            attributes("Script-Class" to (scriptClass ?: "undefined"))
        }
        from(script.output)
        include("com/mikai233/${project.name}/script/*")

        doFirst {
            val containsTarget = script.output.classesDirs.any {
                it.walk().any { file -> file.name == "${classSimpleName}.class" }
            }
            check(containsTarget) { "cannot find ${scriptClass}.class in build dir" }
            check(scriptClass != null) { "missing property scriptClass" }
        }
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

tasks.register("buildAllNodeJar") {
    val releaseDir: String by project
    group = "build"
    dependsOn(getTasksByName("bootJar", true))
    doLast {
        Boot.forEach {
            copy {
                from("${project(it).layout.buildDirectory}/libs")
                into("$releaseDir/$it")
            }
        }
    }
}

tasks.getByName<Delete>("clean") {
    val releaseDir: String by project
    delete.add(releaseDir)
}
