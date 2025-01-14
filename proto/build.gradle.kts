import com.google.protobuf.gradle.id

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