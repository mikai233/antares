plugins {
    groovy
}

dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libAkka.bundles.common)
    implementation(libKotlin.reflect)
    implementation(libKotlinx.core)
    implementation(libKotlinx.datetime.jvm)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(libTool.protobuf.kotlin)
    implementation(libTool.groovy.all)
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
