plugins {
    java
}

group = "com.mikai233"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(akka.bundles.common)
    implementation(ktx.core)
    runtimeOnly(ktx.core.jvm)
    implementation(tool.protobuf.kotlin)
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(project(":proto"))
}

sourceSets {
    create("script") {
        compileClasspath += main.get().run { compileClasspath + output }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Jar>("buildKotlinScript") {
    val scriptClass: String by project
    val classSimpleName = scriptClass.split(".").last()
    archiveFileName.set("${rootProject.name}_${project.name}_${classSimpleName}.jar")
    val script = sourceSets["script"]
    manifest {
        attributes("Script-Class" to scriptClass)
    }
    from(script.output)
    include("com/mikai233/player/script/*")

    doFirst {
        val containsTarget = script.output.classesDirs.any {
            it.walk().any { file -> file.name == "${classSimpleName}.class" }
        }
        check(containsTarget) { "cannot find ${scriptClass}.class in build dir" }
    }
}