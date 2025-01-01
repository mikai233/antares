dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(akka.bundles.common)
//    implementation(akka.discovery)
    implementation(akka.management)
    implementation(akka.management.cluster.http)
    implementation(ktx.core)
    runtimeOnly(ktx.core.jvm)
    implementation(ktor.bundles.common)
    implementation(tool.jcommander)
    implementation(project(":common"))
    implementation(project(":shared"))
}

tasks.test {
    useJUnitPlatform()
}