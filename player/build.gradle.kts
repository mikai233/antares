plugins {
    java
    groovy
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(akka.bundles.common)
    implementation(ktx.core)
    implementation(ktx.datetime.jvm)
    runtimeOnly(ktx.core.jvm)
    implementation(tool.protobuf.kotlin)
    implementation(tool.groovy.all)
    implementation(tool.bundles.koin)
    implementation(tool.spring.data.mongodb)
    implementation(tool.mongodb.driver.sync)
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(project(":proto"))
}

tasks.test {
    useJUnitPlatform()
}
