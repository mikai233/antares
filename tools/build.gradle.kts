import org.springframework.boot.gradle.tasks.bundling.BootJar

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
    implementation(log.bundles.common)
    implementation(ktx.datetime)
    implementation(tool.bundles.curator)
    implementation(tool.guava)
    implementation(project(":common"))
    implementation(project(":shared"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<BootJar>() {
    mainClass.set("com.mikai233.tools.excel.ExcelExporter")
}