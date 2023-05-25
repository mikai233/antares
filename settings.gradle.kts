@file:Suppress("UnstableApiUsage")

rootProject.name = "antares"

val kotlinVersion = "1.8.20"
val kotlinxVersion = "1.7.0"

val akkaVersion = "2.8.1"
val scalaVersion = "2.13"

val ktorClientVersion = "2.3.0"
val ktorServerVersion = "2.3.0"

val jacksonVersion = "2.15.0"

val curatorVersion = "5.5.0"

val junitVersion = "5.9.3"

val protobufVersion = "3.23.0"

val atomicfuVersion = "0.20.2"

val datetimeVersion = "0.4.0"

dependencyResolutionManagement {
    versionCatalogs {
        create("kt") {
            plugin("jvm", "org.jetbrains.kotlin.jvm").version(kotlinVersion)
            plugin("allopen", "org.jetbrains.kotlin.plugin.allopen").version(kotlinVersion)
            plugin("noarg", "org.jetbrains.kotlin.plugin.noarg").version(kotlinVersion)
            library("stdlib", "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
            library("jdk8", "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
            library("reflect", "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
            bundle("common", listOf("stdlib", "jdk8", "reflect"))
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
            library("atomicfu", "org.jetbrains.kotlinx:atomicfu:$atomicfuVersion")
            library("atomicfu.jvm", "org.jetbrains.kotlinx:atomicfu-jvm:$atomicfuVersion")
            library("datetime", "org.jetbrains.kotlinx:kotlinx-datetime:$datetimeVersion")
            library("datetime.jvm", "org.jetbrains.kotlinx:kotlinx-datetime-jvm:$datetimeVersion")
        }
        create("akka") {
            library("actor", "com.typesafe.akka:akka-actor-typed_$scalaVersion:$akkaVersion")
            library("cluster", "com.typesafe.akka:akka-cluster-typed_$scalaVersion:$akkaVersion")
            library("testkit", "com.typesafe.akka:akka-actor-testkit-typed_$scalaVersion:$akkaVersion")
            library("sharding", "com.typesafe.akka:akka-cluster-sharding-typed_$scalaVersion:$akkaVersion")
            library("slf4j", "com.typesafe.akka:akka-slf4j_$scalaVersion:$akkaVersion")
            library("stream", "com.typesafe.akka:akka-stream-typed_$scalaVersion:$akkaVersion")
            bundle("common", listOf("actor", "cluster", "sharding", "slf4j"))
        }
        create("log") {
            library("api", "org.slf4j:slf4j-api:2.0.7")
            library("logback", "ch.qos.logback:logback-classic:1.4.7")
        }
        create("tool") {
            version("protobuf", protobufVersion)

            library("jetcd", "io.etcd:jetcd-core:0.7.5")
            library("curator.framework", "org.apache.curator:curator-framework:$curatorVersion")
            library("curator.recipes", "org.apache.curator:curator-recipes:$curatorVersion")
            library("curator.async", "org.apache.curator:curator-x-async:$curatorVersion")
            bundle("curator", listOf("curator.framework", "curator.recipes", "curator.async"))
            library("jackson.databind", "com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
            library("jackson.kotlin", "com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
            library("jackson.protobuf", "com.fasterxml.jackson.dataformat:jackson-dataformat-protobuf:$jacksonVersion")
            bundle("jackson", listOf("jackson.databind", "jackson.kotlin"))
            library("reflections", "org.reflections:reflections:0.10.2")
            library("protoc", "com.google.protobuf:protoc:$protobufVersion")
            plugin("protobuf", "com.google.protobuf").version("0.9.3")
            library("protobuf.java.util", "com.google.protobuf:protobuf-java-util:$protobufVersion")
            library("protobuf.kotlin", "com.google.protobuf:protobuf-kotlin:$protobufVersion")
            library("netty", "io.netty:netty-all:4.1.92.Final")
            library("lz4", "org.lz4:lz4-java:1.8.0")
            library("bcprov", "org.bouncycastle:bcprov-jdk15on:1.70")
            library("kryo", "io.altoo:akka-kryo-serialization-typed_$scalaVersion:2.5.0")
            plugin("detekt", "io.gitlab.arturbosch.detekt").version("1.23.0-RC3")
            library("caffeine", "com.github.ben-manes.caffeine:caffeine:3.1.6")
            library("groovy", "org.apache.groovy:groovy:4.0.12")
            library("groovy.all", "org.apache.groovy:groovy-all:4.0.12")
            plugin("dokka", "org.jetbrains.dokka").version("1.8.10")
            library("kotlinpoet", "com.squareup:kotlinpoet:1.13.2")
            library("koin", "io.insert-koin:koin-core-jvm:3.4.0")
            library("koin.slf4j", "io.insert-koin:koin-logger-slf4j:3.4.0")
            bundle("koin", listOf("koin", "koin.slf4j"))
            library("poi.ooxml", "org.apache.poi:poi-ooxml:5.2.3")
            library("easyexcel", "com.alibaba:easyexcel:3.3.1")
            plugin("boot", "org.springframework.boot").version("3.1.0")
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
include("shared")
include("stardust")
include("gm")
