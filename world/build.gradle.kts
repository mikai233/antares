plugins {
    groovy
    alias(libs.plugins.ksp)
    alias(libs.plugins.asteria.message.codegen)
}

asteriaMessageCodegen {
    generatedPackage.set("com.mikai233.world.generated")
    moduleId.set("world")
    dispatcherSuperType("PROTOBUF", "com.google.protobuf.GeneratedMessage")
    dispatcherSuperType("INTERNAL", "com.mikai233.common.message.Message")
}

dependencies {
    testImplementation(platform(libs.test.junit.bom))
    testImplementation(libs.test.junit.jupiter)
    implementation(libs.bundles.asteria.foundation)
    implementation(libs.bundles.asteria.cluster)
    implementation(libs.asteria.config.annotations)
    implementation(libs.bundles.asteria.persistence)
    implementation(libs.bundles.asteria.script)
    implementation(libs.bundles.pekko.common)
    implementation(libs.kotlinx.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.reactor)
    implementation(libs.kotlin.reflect)
    implementation(libs.protobuf.kotlin)
    implementation(libs.jcommander)
    implementation(libs.spring.data.mongodb)
    implementation(project(":common"))
    implementation(project(":config"))
    implementation(project(":proto"))
    ksp(libs.asteria.config.ksp)
    ksp(libs.asteria.persistence.mongodb.ksp)
}

tasks.test {
    useJUnitPlatform()
}
