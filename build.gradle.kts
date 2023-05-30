import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(kt.plugins.jvm)
    alias(kt.plugins.allopen)
    alias(kt.plugins.noarg)
    alias(kt.plugins.serialization)
    alias(tool.plugins.detekt)
    alias(tool.plugins.dokka)
    alias(tool.plugins.boot) apply false
}

repositories {
    mavenCentral()
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
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
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
        val scriptClass: String by project
        val classSimpleName = scriptClass.split(".").last()
        archiveFileName.set("${rootProject.name}_${project.name}_${classSimpleName}.jar")
        val script = sourceSets["script"]
        manifest {
            attributes("Script-Class" to scriptClass)
        }
        from(script.output)
        include("com/mikai233/${project.name}/script/*")

        doFirst {
            val containsTarget = script.output.classesDirs.any {
                it.walk().any { file -> file.name == "${classSimpleName}.class" }
            }
            check(containsTarget) { "cannot find ${scriptClass}.class in build dir" }
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
        with(options) {
            encoding = "UTF-8"
            isFork = true
        }
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            javaParameters = true
        }
    }
}