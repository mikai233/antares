plugins {
    java
}

dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(tool.reflections)
    implementation(kt.reflect)
    implementation(tool.bundles.curator)
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
