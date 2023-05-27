repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(tool.reflections)
    implementation(tool.protobuf.kotlin)
    implementation(kt.reflect)
    implementation(akka.bundles.common)
    implementation(tool.caffeine)
    implementation(tool.groovy.all)
    implementation(tool.guava)
    implementation(tool.easyexcel) {
        exclude("org.apache.poi")
    }
    implementation(project(":common"))
    implementation(project(":proto"))
}

tasks.test {
    useJUnitPlatform()
}