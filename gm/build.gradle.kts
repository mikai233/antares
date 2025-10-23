dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(akka.bundles.common)
//    implementation(akka.discovery)
    implementation(akka.management)
    implementation(akka.management.cluster.http)
    implementation(libKotlinx.core)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(ktor.bundles.common)
    implementation(tool.jcommander)
    implementation(tool.kryo)
    implementation(tool.bundles.curator)
    implementation(project(":common"))
}

tasks.test {
    useJUnitPlatform()
}
