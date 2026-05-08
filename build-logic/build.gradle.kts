plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("antaresScriptConventions") {
            id = "antares-script-conventions"
            implementationClass = "com.mikai233.buildlogic.AntaresScriptConventionsPlugin"
        }
    }
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
