plugins {
    application
    alias(libTool.plugins.boot)
}

application {
    mainClass.set("com.mikai233.tools.zookeeper.KubernetesRuntimeConfigInitializerKt")
}

dependencies {
    testImplementation(platform(libTest.junit.bom))
    testImplementation(libTest.junit.jupiter)
    implementation(libTool.bundles.asteria.cluster)
    implementation(libTool.bundles.asteria.config)
    implementation(libTool.bundles.asteria.persistence)
    implementation(libTool.kotlinpoet)
    implementation(libLog.bundles.common)
    implementation(libKotlinx.datetime)
    implementation(libKotlinx.core)
    runtimeOnly(libKotlinx.core.jvm)
    implementation(libKotlin.reflect)
    implementation(libTool.bundles.curator)
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
