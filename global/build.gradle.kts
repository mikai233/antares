plugins {
    groovy
}

dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libPekko.bundles.common)
    implementation(libKotlinx.core)
    implementation(libKotlinx.datetime.jvm)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(libTool.protobuf.kotlin)
    implementation(libTool.groovy.all)
    implementation(libTool.spring.data.mongodb)
    implementation(libTool.mongodb.driver.sync)
    implementation(libTool.jcommander)
    implementation(project(":common"))
}

tasks.test {
    useJUnitPlatform()
}
