plugins {
    groovy
}

dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libAkka.bundles.common)
    implementation(libKotlinx.core)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(libKotlin.reflect)
    implementation(libKotlinx.datetime.jvm)
    implementation(libTool.protobuf.java.util)
    implementation(libTool.protobuf.kotlin)
    implementation(libTool.spring.data.mongodb)
    implementation(libTool.mongodb.driver.sync)
    implementation(libTool.reflections)
    implementation(libTool.jcommander)
    implementation(libTool.guava)
    implementation(project(":common"))
    implementation(project(":proto"))
}

tasks.test {
    useJUnitPlatform()
}
