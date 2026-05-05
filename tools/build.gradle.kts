plugins {
    application
    alias(libs.plugins.boot)
}

application {
    mainClass.set("com.mikai233.tools.zookeeper.KubernetesRuntimeConfigInitializerKt")
}

dependencies {
    testImplementation(platform(libs.test.junit.bom))
    testImplementation(libs.test.junit.jupiter)
    implementation(libs.bundles.asteria.cluster)
    implementation(libs.bundles.asteria.config)
    implementation(libs.bundles.asteria.persistence)
    implementation(libs.kotlinpoet)
    implementation(libs.bundles.log.common)
    implementation(libs.kotlinx.core)
    implementation(libs.bundles.curator)
    implementation(project(":common"))
}

tasks.test {
    useJUnitPlatform()
}

fun JavaExec.forwardGameConfigVersion() {
    val configVersion = providers.gradleProperty("gameConfigVersion")
        .orNull
        ?.takeIf(String::isNotBlank)
        ?: project.version.toString()
    systemProperty("gameConfigVersion", configVersion)
}

tasks.register<JavaExec>("publishLocalGameConfig") {
    group = "antares config"
    description =
        "Regenerate the Luban binary bundle and publish game config to local Zookeeper. Defaults to projectVersion; use -PgameConfigVersion=x.y.z to override."
    dependsOn(":common:packageLubanConfigBundle", "classes")
    mainClass = "com.mikai233.tools.config.PublishLocalGameConfigKt"
    classpath = sourceSets["main"].runtimeClasspath
    forwardGameConfigVersion()
}

tasks.register<JavaExec>("initializeLocalRuntimeConfig") {
    group = "antares local"
    description =
        "Initialize local topology/runtime config and publish game config. Defaults to projectVersion; use -PgameConfigVersion=x.y.z to override."
    dependsOn(":common:packageLubanConfigBundle", "classes")
    mainClass = "com.mikai233.tools.zookeeper.ZookeeperInitializerKt"
    classpath = sourceSets["main"].runtimeClasspath
    forwardGameConfigVersion()
}

tasks.register<JavaExec>("initializeKubernetesRuntimeConfig") {
    group = "antares deploy"
    description = "Initialize Kubernetes runtime config keys without publishing cluster topology."
    dependsOn("classes")
    mainClass = application.mainClass
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.bootJar {
    mainClass.set(application.mainClass)
}
