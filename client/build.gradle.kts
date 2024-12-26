plugins {
    java
}

dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(ktx.datetime.jvm)
    implementation(tool.netty)
    implementation(tool.lz4)
    implementation(tool.protobuf.kotlin)
    implementation(tool.protobuf.java.util)
    implementation(tool.bundles.jackson)
    implementation(tool.jackson.yaml)
    implementation(tool.reflections)
    runtimeOnly(log.logback)
//    implementation(log.api)
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(project(":proto"))
}

tasks.test {
    useJUnitPlatform()
}
