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
    implementation(ktx.datetime.jvm)
    implementation(tool.protobuf.java.util)
    implementation(tool.protobuf.kotlin)
    implementation(tool.bundles.koin)
    implementation(tool.spring.data.mongodb)
    implementation(tool.mongodb.driver.sync)
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(project(":proto"))
}

tasks.test {
    useJUnitPlatform()
}