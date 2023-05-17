plugins {

}

group = "com.mikai233"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(project(":common"))
    implementation(project(":gate"))
    implementation(project(":player"))
    implementation(project(":world"))
}

tasks.test {
    useJUnitPlatform()
}