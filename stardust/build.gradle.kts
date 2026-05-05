plugins {
    application
}

application {
    mainClass.set("com.mikai233.stardust.StardustKt")
}

dependencies {
    testImplementation(platform(libs.test.junit.bom))
    testImplementation(libs.test.junit.jupiter)
    implementation(libs.bundles.asteria.cluster)
    implementation(libs.bundles.asteria.config)
    implementation(libs.pekko.actor)
    implementation(libs.bundles.log.common)
    implementation(libs.kotlinx.core)
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

tasks.register("prepareLocalDev") {
    group = "antares local"
    description = "Prepare local Zookeeper runtime config and game-config publication for Stardust."
    dependsOn(":tools:initializeLocalRuntimeConfig")
}

tasks.named<JavaExec>("run") {
    description = "Launch the Stardust all-in-one local development cluster."
}
