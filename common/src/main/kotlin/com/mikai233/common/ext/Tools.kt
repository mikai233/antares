package com.mikai233.common.ext

import com.google.protobuf.util.JsonFormat
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.conf.ServerMode
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.slf4j.LoggerFactory
import java.net.Inet4Address
import java.net.NetworkInterface

fun getMachineIp(): String {
    return getInet4Address()?.hostAddress ?: "127.0.0.1"
}

fun getInet4Address(): Inet4Address? {
    val inet4Addresses =
        NetworkInterface.networkInterfaces()
            .filter { !it.isLoopback && it.inetAddresses().anyMatch { address -> address is Inet4Address } }
            .flatMap { it.inetAddresses().filter { inetAddress -> inetAddress is Inet4Address } }.toList()
    return inet4Addresses.firstOrNull() as Inet4Address?
}

fun getenv(name: String): String? = System.getenv(name)

fun buildSimpleZkClient(connectionString: String): CuratorFramework {
    val retryPolicy = ExponentialBackoffRetry(1000, 3)
    return CuratorFrameworkFactory.builder()
        .connectString(connectionString)
        .sessionTimeoutMs(5000)
        .connectionTimeoutMs(5000)
        .retryPolicy(retryPolicy)
        .build()
}

enum class Platform {
    Windows, MacOS, Linux, Unknown
}

fun getPlatform(): Platform {
    val os = System.getProperty("os.name")
    return when {
        os.contains("Windows", true) -> Platform.Windows
        os.contains("Mac OS", true) -> Platform.MacOS
        os.contains("Linux", true) -> Platform.Linux
        else -> {
            LoggerFactory.getLogger("ToolsKt").warn("unknown os:{}", os)
            Platform.Unknown
        }
    }
}

fun protobufJsonPrinter(): JsonFormat.Printer =
    JsonFormat.printer().omittingInsignificantWhitespace().includingDefaultValueFields()

fun unixTimestamp() = System.currentTimeMillis()

fun invokeOnTargetMode(modes: Set<ServerMode>, block: () -> Unit) {
    if (modes.contains(GlobalEnv.serverMode)) {
        block.invoke()
    }
}