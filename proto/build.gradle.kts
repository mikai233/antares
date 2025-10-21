import com.google.protobuf.gradle.id
import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(tool.plugins.protobuf)
}

protobuf {
    protoc {
        val version = tool.versions.protobuf.get()
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
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(tool.protobuf.kotlin)
    implementation(tool.reflections)
    implementation(kt.reflect)
    implementation(tool.kotlinpoet)
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
