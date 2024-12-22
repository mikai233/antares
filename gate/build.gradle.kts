plugins {
    java
}

dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(akka.bundles.common)
    implementation(kt.reflect)
    implementation(ktx.core)
    runtimeOnly(ktx.core.jvm)
    implementation(log.bundles.common)
    implementation(tool.netty)
    implementation(tool.lz4)
    implementation(tool.reflections)
    implementation(tool.protobuf.kotlin)
    implementation(tool.protobuf.java.util)
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(project(":proto"))
    implementation(project(":player"))
    implementation(project(":world"))
}

tasks.test {
    useJUnitPlatform()
}
