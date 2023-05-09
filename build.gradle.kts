plugins {
    @Suppress("DSL_SCOPE_VIOLATION") alias(kt.plugins.jvm)
}

group = "com.mikai233"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

subprojects {
    apply(plugin = "kotlin")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}