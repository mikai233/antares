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
    implementation(ktx.core)
    runtimeOnly(ktx.core.jvm)
    implementation(tool.netty)
    implementation(tool.lz4)
    implementation(tool.protobuf.kotlin)
    implementation(tool.protobuf.java.util)
    implementation(tool.bundles.koin)
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(project(":proto"))
}

tasks.test {
    useJUnitPlatform()
}