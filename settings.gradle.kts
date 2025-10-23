plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "antares"

val kotlinVersion = "2.1.0"
val kotlinxVersion = "1.10.0"
val akkaVersion = "2.10.0"
val akkaManagementVersion = "1.6.0"
val scalaVersion = "2.13"
val ktorClientVersion = "3.0.3"
val ktorServerVersion = "3.0.3"
val jacksonVersion = "2.18.2"
val curatorVersion = "5.7.1"
val junitVersion = "5.9.3"
val protobufVersion = "4.29.2"
val atomicfuVersion = "0.26.1"
val datetimeVersion = "0.6.1"
val kotlinpoetVersion = "2.0.0"
val kspVersion = "1.0.29"
val prometheusVersion = "0.16.0"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://repo.akka.io/${System.getenv("AKKA_SECURE_TOKEN")}/secure")
        mavenCentral()
    }
    versionCatalogs {
        create("libKotlin") {
            from(files("gradle/lib.kotlin.versions.toml"))
        }
        create("ktor") {
            from(files("gradle/ktor.versions.toml"))
        }
        create("libKotlinx") {
            from(files("gradle/lib.kotlinx.versions.toml"))
        }
        create("libAkka") {
            from(files("gradle/lib.akka.versions.toml"))
        }
        create("log") {
            from(files("gradle/log.versions.toml"))
        }
        create("tool") {
            from(files("gradle/tool.versions.toml"))
        }
        create("libTest") {
            from(files("gradle/lib.test.versions.toml"))
        }
    }
}

include("common")
include("gate")
include("tools")
include("player")
include("proto")
include("global")
include("world")
include("stardust")
include("gm")
include("processor")
