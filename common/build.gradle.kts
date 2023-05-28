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
    implementation(kt.bundles.common)
    implementation(ktx.core)
    runtimeOnly(ktx.core.jvm)
    implementation(ktx.datetime)
    implementation(tool.bundles.curator)
    runtimeOnly(log.logback)
    implementation(log.api)
    implementation(tool.bundles.jackson)
    implementation(tool.jackson.protobuf)
    implementation(tool.reflections)
    implementation(tool.protobuf.kotlin)
    implementation(tool.protobuf.java.util)
    implementation(tool.bcprov)
    implementation(tool.kryo)
    implementation(tool.groovy.all)
    implementation(tool.bundles.koin)
    implementation(tool.guava)
    implementation(tool.poi.ooxml)
    implementation(tool.easyexcel)
}

tasks.test {
    useJUnitPlatform()
}