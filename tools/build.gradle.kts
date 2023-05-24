plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(tool.kotlinpoet)
    implementation(project(":common"))
}

tasks.test {
    useJUnitPlatform()
}