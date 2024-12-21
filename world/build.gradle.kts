plugins {
    java
//    alias(tool.plugins.ksp)
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
    implementation(ktx.serialization.core)
    implementation(ktx.datetime.jvm)
    implementation(tool.protobuf.java.util)
    implementation(tool.protobuf.kotlin)
    implementation(tool.bundles.koin)
    implementation(tool.spring.data.mongodb)
    implementation(tool.mongodb.driver.sync)
    implementation(tool.reflections)
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(project(":proto"))
//    implementation(project(":processor"))
//    ksp(project(":processor"))
}

tasks.test {
    useJUnitPlatform()
}
