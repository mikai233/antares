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
        create("ktx") {
            library("core", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.0")
            library("core.jvm", "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.0")
            library("atomicfu", "org.jetbrains.kotlinx:atomicfu:$atomicfuVersion")
            library("atomicfu.jvm", "org.jetbrains.kotlinx:atomicfu-jvm:$atomicfuVersion")
            library("datetime", "org.jetbrains.kotlinx:kotlinx-datetime:$datetimeVersion")
            library("datetime.jvm", "org.jetbrains.kotlinx:kotlinx-datetime-jvm:$datetimeVersion")
        }
        create("akka") {
            library("actor", "com.typesafe.akka:akka-actor_$scalaVersion:$akkaVersion")
            library("cluster", "com.typesafe.akka:akka-cluster_$scalaVersion:$akkaVersion")
            library("testkit", "com.typesafe.akka:akka-actor-testkit_$scalaVersion:$akkaVersion")
            library("sharding", "com.typesafe.akka:akka-cluster-sharding_$scalaVersion:$akkaVersion")
            library("slf4j", "com.typesafe.akka:akka-slf4j_$scalaVersion:$akkaVersion")
            library("stream", "com.typesafe.akka:akka-stream_$scalaVersion:$akkaVersion")
            bundle("common", listOf("actor", "cluster", "sharding", "slf4j"))
            library("discovery", "com.typesafe.akka:akka-discovery_$scalaVersion:$akkaVersion")
            library(
                "management",
                "com.lightbend.akka.management:akka-management_$scalaVersion:$akkaManagementVersion",
            )
            library(
                "management.cluster.http",
                "com.lightbend.akka.management:akka-management-cluster-http_$scalaVersion:$akkaManagementVersion",
            )
        }
        create("log") {
            from(files("gradle/log.versions.toml"))
        }
        create("tool") {
            from(files("gradle/tool.versions.toml"))
        }
        create("test") {
            library("junit.bom", "org.junit:junit-bom:$junitVersion")
            library("junit.jupiter", "org.junit.jupiter:junit-jupiter:$junitVersion")
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
