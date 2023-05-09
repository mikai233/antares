@file:Suppress("UnstableApiUsage")

rootProject.name = "akka-game-server"

val kotlinVersion = "1.8.20"
val kotlinxVersion = "1.7.0"

val akkaVersion = "2.8.1"
val scalaVersion = "2.13"

val ktorClientVersion = "2.3.0"
val ktorServerVersion = "2.3.0"

dependencyResolutionManagement {
    versionCatalogs {
        create("kt") {
            plugin("jvm", "org.jetbrains.kotlin.jvm").version(kotlinVersion)
            library("stdlib", "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
            library("jdk8", "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
            library("reflect", "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
        }
        create("ktor") {
//            library("client.core", "io.ktor:ktor-client-core:$ktorClientVersion")
            library("client.cio.jvm", "io.ktor:ktor-client-cio-jvm:$ktorClientVersion")
//            library("server.core", "io.ktor:ktor-server-core:$ktorServerVersion")
            library("server.netty", "io.ktor:ktor-server-netty:$ktorServerVersion")
        }
        create("ktx") {
            library("core", "org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxVersion")
            library("core.jvm", "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinxVersion")
        }
        create("akka") {
            library("actor", "com.typesafe.akka:akka-actor-typed_$scalaVersion:$akkaVersion")
            library("cluster", "com.typesafe.akka:akka-cluster-typed_$scalaVersion:$akkaVersion")
            library("testkit", "com.typesafe.akka:akka-actor-testkit-typed_$scalaVersion:$akkaVersion")
            library("sharding", "com.typesafe.akka:akka-cluster-sharding-typed_$scalaVersion:$akkaVersion")
            library("slf4j", "com.typesafe.akka:akka-slf4j_$scalaVersion:$akkaVersion")
            library("stream", "com.typesafe.akka:akka-stream-typed_$scalaVersion:$akkaVersion")
        }
        create("log") {
            library("api", "org.slf4j:slf4j-api:2.0.7")
            library("logback", "ch.qos.logback:logback-classic:1.4.7")
        }
    }
}
include("common")
include("gate")
