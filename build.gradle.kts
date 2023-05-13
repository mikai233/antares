plugins {
    @Suppress("DSL_SCOPE_VIOLATION") alias(kt.plugins.jvm)
    @Suppress("DSL_SCOPE_VIOLATION") alias(kt.plugins.allopen)
    @Suppress("DSL_SCOPE_VIOLATION") alias(kt.plugins.noarg)
}

group = "com.mikai233"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

subprojects {
    apply(plugin = "kotlin")
    apply(plugin = "kotlin-allopen")
    apply(plugin = "kotlin-noarg")

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
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}