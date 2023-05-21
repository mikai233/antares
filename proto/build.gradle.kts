import com.google.protobuf.gradle.id

plugins {
    @Suppress("DSL_SCOPE_VIOLATION") alias(tool.plugins.protobuf)
}

repositories {
    mavenCentral()
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
}

tasks.test {
    useJUnitPlatform()
}