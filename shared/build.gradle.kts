plugins {
    alias(tool.plugins.ksp)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(tool.reflections)
    implementation(tool.protobuf.kotlin)
    implementation(kt.reflect)
    implementation(ktx.bundles.serialization)
    implementation(akka.bundles.common)
    implementation(tool.caffeine)
    implementation(tool.groovy.all)
    implementation(tool.guava)
    implementation(tool.lz4)
    implementation(tool.koin)
    implementation(tool.bundles.jackson)
    implementation(tool.symbol.processing)
    implementation(tool.spring.data.mongodb)
    implementation(tool.easyexcel) {
        exclude("org.apache.poi")
    }
    implementation(project(":common"))
    implementation(project(":proto"))
    implementation(project(":processor"))
    ksp(project(":processor"))
}

tasks.test {
    useJUnitPlatform()
}