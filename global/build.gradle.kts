plugins {
    groovy
}

dependencies {
    testImplementation(platform(libs.test.junit.bom))
    testImplementation(libs.test.junit.jupiter)
    implementation(libs.bundles.asteria.foundation)
    implementation(libs.bundles.asteria.cluster)
    implementation(libs.bundles.asteria.script)
    implementation(libs.bundles.pekko.common)
    implementation(libs.kotlinx.core)
    implementation(libs.protobuf.kotlin)
    implementation(libs.groovy.all)
    implementation(libs.jcommander)
    implementation(project(":common"))
    implementation(project(":server-proto"))
}

tasks.test {
    useJUnitPlatform()
}
