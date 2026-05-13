dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(platform(libs.test.junit.bom))
    testImplementation(libs.test.junit.jupiter)
    testImplementation(libs.spring.boot.starter.test)
    implementation(libs.bundles.asteria.foundation)
    implementation(libs.bundles.asteria.cluster)
    implementation(libs.bundles.asteria.config)
    implementation(libs.bundles.asteria.script)
    implementation(libs.asteria.gm.admin.spring.boot.starter)
    implementation(libs.asteria.gm.cluster.pekko)
    implementation(libs.asteria.gm.cluster.pekko.management.spring.boot.starter)
    implementation(libs.asteria.script.job.mongodb.spring.boot.starter)
    implementation(libs.bundles.pekko.common)
    implementation(libs.kotlinx.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.reactor)
    implementation(libs.bundles.spring.boot.web)
    implementation(libs.jcommander)
    implementation(libs.kotlin.reflect)
    implementation(project(":config"))
    implementation(project(":common"))
    implementation(project(":proto"))
}

tasks.test {
    useJUnitPlatform()
}
