plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(akka.bundles.common)
    implementation(kt.reflect)
    implementation(ktx.core)
    runtimeOnly(ktx.core.jvm)
    implementation(ktx.serialization.core)
    implementation(log.bundles.common)
    implementation(tool.netty)
    implementation(tool.lz4)
    implementation(tool.reflections)
    implementation(tool.protobuf.kotlin)
    implementation(tool.protobuf.java.util)
    implementation(tool.bundles.koin)
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(project(":proto"))
    implementation(project(":player"))
    implementation(project(":world"))
}

tasks.test {
    useJUnitPlatform()
}
