plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "antares"

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
        create("libKtor") {
            from(files("gradle/lib.ktor.versions.toml"))
        }
        create("libKotlinx") {
            from(files("gradle/lib.kotlinx.versions.toml"))
        }
        create("libAkka") {
            from(files("gradle/lib.akka.versions.toml"))
        }
        create("libLog") {
            from(files("gradle/lib.log.versions.toml"))
        }
        create("libTool") {
            from(files("gradle/lib.tool.versions.toml"))
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
