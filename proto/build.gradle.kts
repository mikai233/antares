import com.google.protobuf.gradle.id
import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libTool.plugins.protobuf)
}

protobuf {
    protoc {
        val version = libTool.versions.protobuf.get()
        artifact = "com.google.protobuf:protoc:$version"
    }
    generateProtoTasks {
        all().forEach {
            it.builtins {
                id("kotlin")
            }
        }
    }
}

tasks.withType<Detekt>().configureEach {
    exclude(
        "com/mikai233/protocol/ClientToServer.kt",
        "com/mikai233/protocol/ServerToClient.kt",
    )
}

dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libTool.protobuf.kotlin)
    implementation(libTool.reflections)
    implementation(libKotlin.reflect)
    implementation(libTool.kotlinpoet)
}

tasks.register<JavaExec>("generateProtoMeta") {
    group = "other"
    mainClass = "com.mikai233.protocol.MessageMetaGeneratorKt"
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.named("generateProto") {
    finalizedBy("generateProtoMeta")
}

tasks.test {
    useJUnitPlatform()
}
