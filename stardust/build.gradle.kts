dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libKotlin.reflect)
    implementation(libKotlinx.core)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(libTool.bundles.curator)
    implementation(libAkka.actor)
    implementation(libLog.logback)
    implementation(project(":common"))
    implementation(project(":gate"))
    implementation(project(":player"))
    implementation(project(":world"))
    implementation(project(":gm"))
    implementation(project(":global"))
}

tasks.test {
    useJUnitPlatform()
}
