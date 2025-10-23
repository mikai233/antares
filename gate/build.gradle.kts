plugins {
    groovy
}

dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(akka.bundles.common)
    implementation(libKotlin.reflect)
    implementation(ktx.core)
    runtimeOnly(ktx.core.jvm)
    implementation(log.bundles.common)
    implementation(tool.netty)
    implementation(tool.lz4)
    implementation(tool.reflections)
    implementation(tool.protobuf.kotlin)
    implementation(tool.protobuf.java.util)
    implementation(tool.bundles.curator)
    implementation(tool.jcommander)
    implementation(project(":common"))
    implementation(project(":proto"))
}

tasks.test {
    useJUnitPlatform()
}
