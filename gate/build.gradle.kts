group = "com.mikai233"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(akka.actor)
    implementation(ktx.core)
    runtimeOnly(ktx.core.jvm)
    implementation(tool.netty)
    implementation(tool.lz4)
    implementation(tool.protobuf.kotlin)
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(project(":proto"))
}

tasks.test {
    useJUnitPlatform()
}