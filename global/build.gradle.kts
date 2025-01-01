plugins {
    groovy
}

dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(akka.bundles.common)
    implementation(ktx.core)
    implementation(ktx.datetime.jvm)
    runtimeOnly(ktx.core.jvm)
    implementation(tool.groovy.all)
    implementation(tool.spring.data.mongodb)
    implementation(tool.mongodb.driver.sync)
    implementation(tool.jcommander)
    implementation(project(":common"))
    implementation(project(":shared"))
}

tasks.test {
    useJUnitPlatform()
}
