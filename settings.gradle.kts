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
        maven("https://repo.akka.io/maven/")
        mavenCentral()
    }
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
            library("client.core", "io.ktor:ktor-client-core-jvm:$ktorClientVersion")
            library("client.cio", "io.ktor:ktor-client-cio-jvm:$ktorClientVersion")
            library("server.netty", "io.ktor:ktor-server-netty-jvm:$ktorServerVersion")
            library("server.host.common", "io.ktor:ktor-server-host-common-jvm:$ktorServerVersion")
            library("server.content.negotiation", "io.ktor:ktor-server-content-negotiation-jvm:$ktorServerVersion")
            library("serialization.jackson", "io.ktor:ktor-serialization-jackson-jvm:$ktorServerVersion")
            library("server.status.pages", "io.ktor:ktor-server-status-pages-jvm:$ktorServerVersion")
            library("server.request.validation", "io.ktor:ktor-server-request-validation-jvm:$ktorServerVersion")
            library("server.cors", "io.ktor:ktor-server-cors-jvm:$ktorServerVersion")
            bundle(
                "common",
                listOf(
                    "client.core",
                    "client.cio",
                    "server.netty",
                    "server.host.common",
                    "server.content.negotiation",
                    "serialization.jackson",
                    "server.status.pages",
                    "server.request.validation",
                    "server.cors",
                ),
            )
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
//            library("api", "org.slf4j:slf4j-api:2.0.7")
            library("logback", "ch.qos.logback:logback-classic:1.5.15")
            library("log4j.to.slf4j", "org.apache.logging.log4j:log4j-to-slf4j:2.20.0")
            library("janino", "org.codehaus.janino:janino:3.1.12")
            bundle("common", listOf("logback", "log4j.to.slf4j", "janino"))
        }
        create("tool") {
            version("protobuf", protobufVersion)

            library("curator.framework", "org.apache.curator:curator-framework:$curatorVersion")
            library("curator.recipes", "org.apache.curator:curator-recipes:$curatorVersion")
            library("curator.async", "org.apache.curator:curator-x-async:$curatorVersion")
            bundle("curator", listOf("curator.framework", "curator.recipes", "curator.async"))
            library("jackson.databind", "com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
            library("jackson.kotlin", "com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
            library("jackson.protobuf", "com.fasterxml.jackson.dataformat:jackson-dataformat-protobuf:$jacksonVersion")
            library("jackson.guava", "com.fasterxml.jackson.datatype:jackson-datatype-guava:$jacksonVersion")
            library("jackson.cbor", "com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:$jacksonVersion")
            library("jackson.yaml", "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
            library("jackson.datatype.jsr310", "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
            library("jackson.datatype.jdk8", "com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
            bundle(
                "jackson",
                listOf(
                    "jackson.databind",
                    "jackson.kotlin",
                    "jackson.guava",
                    "jackson.datatype.jsr310",
                    "jackson.datatype.jdk8",
                ),
            )
            library("reflections", "org.reflections:reflections:0.10.2")
            library("protoc", "com.google.protobuf:protoc:$protobufVersion")
            plugin("protobuf", "com.google.protobuf").version("0.9.3")
            library("protobuf.java.util", "com.google.protobuf:protobuf-java-util:$protobufVersion")
            library("protobuf.kotlin", "com.google.protobuf:protobuf-kotlin:$protobufVersion")
            library("netty", "io.netty:netty-all:4.1.92.Final")
            library("lz4", "org.lz4:lz4-java:1.8.0")
            library("bcprov", "org.bouncycastle:bcprov-jdk15on:1.70")
            library("akka.kryo", "io.altoo:akka-kryo-serialization_$scalaVersion:2.5.0")
            plugin("detekt", "io.gitlab.arturbosch.detekt").version("1.23.7")
            library("caffeine", "com.github.ben-manes.caffeine:caffeine:3.1.6")
            library("groovy", "org.apache.groovy:groovy:4.0.12")
            library("groovy.all", "org.apache.groovy:groovy-all:4.0.12")
            plugin("dokka", "org.jetbrains.dokka").version("1.8.10")
            library("kotlinpoet", "com.squareup:kotlinpoet:$kotlinpoetVersion")
            library("kotlinpoet.ksp", "com.squareup:kotlinpoet-ksp:$kotlinpoetVersion")
            library("easyexcel", "com.alibaba:easyexcel:4.0.3")
            plugin("boot", "org.springframework.boot").version("3.4.1")
            library("guava", "com.google.guava:guava:33.4.0-jre")
            library("symbol.processing.api", "com.google.devtools.ksp:symbol-processing-api:$kotlinVersion-$kspVersion")
//            library("symbol.processing", "com.google.devtools.ksp:symbol-processing:$kotlinVersion-$kspVersion")
            plugin("ksp", "com.google.devtools.ksp").version("${kotlinVersion}-${kspVersion}")
            library("spring.data.mongodb", "org.springframework.data:spring-data-mongodb:4.1.0")
            library("spring.retry", "org.springframework.retry:spring-retry:2.0.11")
            library("mongodb.driver.sync", "org.mongodb:mongodb-driver-sync:4.9.1")
            library("agrona", "org.agrona:agrona:1.18.1")
            library("kryo", "com.esotericsoftware:kryo:5.6.2")
            library("jcommander", "org.jcommander:jcommander:2.0")
            library("simpleclient", "io.prometheus:simpleclient:$prometheusVersion")
            library("simpleclient_hotspot", "io.prometheus:simpleclient_hotspot:$prometheusVersion")
            library("simpleclient_httpserver", "io.prometheus:simpleclient_httpserver:$prometheusVersion")
            bundle("prometheus", listOf("simpleclient", "simpleclient_hotspot", "simpleclient_httpserver"))
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
