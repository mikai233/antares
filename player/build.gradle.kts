plugins {
    groovy
//    alias(tool.plugins.ksp)
}

dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(akka.bundles.common)
    implementation(kt.reflect)
    implementation(ktx.core)
    implementation(ktx.datetime.jvm)
    runtimeOnly(ktx.core.jvm)
    implementation(tool.protobuf.kotlin)
    implementation(tool.groovy.all)
    implementation(tool.spring.data.mongodb)
    implementation(tool.mongodb.driver.sync)
    implementation(tool.reflections)
    implementation(tool.jcommander)
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(project(":proto"))
    implementation(project(":processor"))
//    ksp(project(":processor"))
}

tasks.test {
    useJUnitPlatform()
}
