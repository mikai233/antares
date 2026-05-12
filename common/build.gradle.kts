plugins {
    alias(libs.plugins.ksp)
}

dependencies {
    testImplementation(platform(libs.test.junit.bom))
    testImplementation(libs.test.junit.jupiter)
    implementation(libs.bundles.asteria.foundation)
    implementation(libs.bundles.asteria.cluster)
    implementation(libs.bundles.asteria.config)
    implementation(libs.bundles.asteria.persistence)
    implementation(libs.bundles.asteria.script)
    implementation(libs.bundles.pekko.common)
    implementation(libs.pekko.discovery)
    implementation(libs.pekko.discovery.kubernetes.api)
    implementation(libs.pekko.management)
    implementation(libs.pekko.management.cluster.bootstrap)
    implementation(libs.pekko.management.cluster.http)
    implementation(libs.bundles.kotlin.common)
    implementation(libs.kotlinx.core)
    implementation(libs.kotlinx.reactor)
    implementation(libs.kotlinx.datetime)
    implementation(libs.bundles.curator)
    implementation(libs.bundles.log.common)
    testImplementation(libs.reflections)
    testImplementation(libs.spring.data.mongodb)
    testImplementation(libs.testcontainers.mongodb)
    implementation(libs.protobuf.kotlin)
    implementation(libs.protobuf.java.util)
    implementation(libs.groovy.all)
    implementation(libs.guava)
    compileOnly(libs.spring.data.commons)
    implementation(libs.spring.data.mongodb)
    implementation(libs.bundles.prometheus)
    implementation(project(":config"))
    implementation(project(":proto"))
    ksp(libs.asteria.persistence.mongodb.ksp)
}

tasks.test {
    useJUnitPlatform()
}

tasks.matching { it.name == "kspTestKotlin" }.configureEach {
    onlyIf {
        configurations.findByName("kspTest")?.allDependencies?.isNotEmpty() == true
    }
}
