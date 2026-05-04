plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.protobuf:protobuf-java:4.34.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
    implementation("com.squareup:kotlinpoet:2.3.0")
}

tasks.test {
    useJUnitPlatform()
}
