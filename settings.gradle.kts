rootProject.name = "antares"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
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
        create("libPekko") {
            from(files("gradle/lib.pekko.versions.toml"))
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
