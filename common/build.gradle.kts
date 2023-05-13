plugins {
    id("java")
}

group = "com.mikai233"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(akka.cluster)
    implementation(akka.slf4j)
    implementation(kt.bundles.common)
    implementation(ktx.core)
    runtimeOnly(ktx.core.jvm)
    implementation(tool.bundles.curator)
    runtimeOnly(log.logback)
    implementation(log.api)
    implementation(tool.bundles.jackson)
    implementation(tool.jackson.protobuf)
    implementation(tool.reflections)
    implementation(tool.protobuf.kotlin)
    implementation(tool.bcprov)
}

tasks.test {
    useJUnitPlatform()
}