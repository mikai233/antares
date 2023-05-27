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
    implementation(tool.guava)
    implementation(project(":common"))
}

tasks.test {
    useJUnitPlatform()
}