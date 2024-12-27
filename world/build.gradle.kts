plugins {
    java
//    alias(tool.plugins.ksp)
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
    implementation(tool.spring.data.mongodb)
    implementation(tool.mongodb.driver.sync)
    implementation(tool.reflections)
    implementation(tool.jcommander)
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(project(":proto"))
//    implementation(project(":processor"))
//    ksp(project(":processor"))
}

tasks.test {
    useJUnitPlatform()
}
