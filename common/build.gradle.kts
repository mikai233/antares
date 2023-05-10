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
    implementation(kt.bundles.common)
    implementation(ktx.core)
    runtimeOnly(ktx.core.jvm)
    implementation(tool.bundles.curator)
    runtimeOnly(log.logback)
    implementation(log.api)
    implementation(tool.bundles.jackson)
}

tasks.test {
    useJUnitPlatform()
}