plugins {
//    alias(tool.plugins.ksp)
}

dependencies {
    testImplementation(platform(test.junit.bom))
    testImplementation(test.junit.jupiter)
    implementation(tool.reflections)
    implementation(tool.protobuf.kotlin)
    implementation(tool.protobuf.java.util)
    implementation(kt.reflect)
    implementation(ktx.core)
    implementation(akka.bundles.common)
    implementation(tool.caffeine)
    implementation(tool.groovy.all)
    implementation(tool.guava)
    implementation(tool.lz4)
    implementation(tool.netty)
    implementation(tool.bundles.jackson)
//    implementation(tool.symbol.processing)
    implementation(tool.spring.data.mongodb)
    implementation(tool.easyexcel)
    implementation(tool.kryo)
    runtimeOnly(log.bundles.common)
    implementation(project(":common"))
    implementation(project(":proto"))
//    implementation(project(":processor"))
//    ksp(project(":processor"))
}

tasks.test {
    useJUnitPlatform()
}
