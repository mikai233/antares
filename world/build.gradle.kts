plugins {
    groovy
}

dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(akka.bundles.common)
    implementation(ktx.core)
    runtimeOnly(ktx.core.jvm)
    implementation(kt.reflect)
    implementation(ktx.datetime.jvm)
    implementation(tool.protobuf.java.util)
    implementation(tool.protobuf.kotlin)
    implementation(tool.spring.data.mongodb)
    implementation(tool.mongodb.driver.sync)
    implementation(tool.reflections)
    implementation(tool.jcommander)
    implementation(tool.guava)
    implementation(project(":common"))
    implementation(project(":proto"))
}

tasks.test {
    useJUnitPlatform()
}
