plugins {
    id("java")
}

group = "com.mikai233"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation(akka.cluster)
    implementation(kt.reflect)
}

tasks.test {
    useJUnitPlatform()
}