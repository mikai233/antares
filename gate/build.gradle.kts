group = "com.mikai233"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(akka.actor)
    implementation(ktx.core)
    runtimeOnly(ktx.core.jvm)
}

tasks.test {
    useJUnitPlatform()
}