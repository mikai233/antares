dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(libKotlin.reflect)
    implementation(ktx.core)
    runtimeOnly(ktx.core.jvm)
    implementation(tool.bundles.curator)
    implementation(akka.actor)
    implementation(log.logback)
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
