plugins {
    application
}

application {
    mainClass.set("com.mikai233.stardust.StardustKt")
}

dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libTool.bundles.asteria.cluster)
    implementation(libTool.bundles.asteria.config)
    implementation(libPekko.actor)
    implementation(libLog.bundles.common)
    implementation(libKotlinx.core)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(libKotlin.reflect)
    implementation(project(":common"))
    implementation(project(":gate"))
    implementation(project(":global"))
    implementation(project(":gm"))
    implementation(project(":player"))
    implementation(project(":world"))
}

tasks.test {
    useJUnitPlatform()
}
