pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "antares"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

include("common")
include("config")
include("gate")
include("stardust")
include("tools")
include("player")
include("client-proto")
include("server-proto")
include("global")
include("world")
include("gm")
