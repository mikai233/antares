package com.mikai233.common.runtime

import java.util.Properties

data class RuntimeBuildInfo(
    val appName: String,
    val version: String,
)

object RuntimeBuildInfoLoader {
    private const val BUILD_INFO_RESOURCE = "META-INF/antares/build-info.properties"

    fun load(): RuntimeBuildInfo {
        val classLoader = Thread.currentThread().contextClassLoader ?: RuntimeBuildInfoLoader::class.java.classLoader
        val properties = classLoader.getResourceAsStream(BUILD_INFO_RESOURCE)?.use { input ->
            Properties().also { it.load(input) }
        } ?: error("runtime build-info resource not found: $BUILD_INFO_RESOURCE")
        val version = properties.getProperty("version")?.trim()?.takeIf { it.isNotEmpty() }
            ?: error("runtime build-info version must not be blank")
        val appName = properties.getProperty("appName")?.trim()?.takeIf { it.isNotEmpty() }
            ?: error("runtime build-info appName must not be blank")
        return RuntimeBuildInfo(
            appName = appName,
            version = version,
        )
    }
}

fun runtimeBuildInfo(): RuntimeBuildInfo = RuntimeBuildInfoLoader.load()

fun runtimeVersion(): String = runtimeBuildInfo().version
