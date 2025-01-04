package com.mikai233.common.extension

import com.google.common.base.CaseFormat
import com.google.protobuf.util.JsonFormat
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.conf.ServerMode
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.x.async.AsyncCuratorFramework
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.Inet4Address
import java.net.NetworkInterface

private val logger = LoggerFactory.getLogger("ToolsKt")

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

fun asyncZookeeperClient(connectionString: String): AsyncCuratorFramework {
    val retryPolicy = ExponentialBackoffRetry(1000, 3)
    val client = CuratorFrameworkFactory.builder()
        .connectString(connectionString)
        .sessionTimeoutMs(5000)
        .connectionTimeoutMs(5000)
        .retryPolicy(retryPolicy)
        .build()
    client.start()
    return AsyncCuratorFramework.wrap(client)
}

enum class Platform {
    Windows,
    MacOS,
    Linux,
    Unknown
}

fun getPlatform(): Platform {
    val os = System.getProperty("os.name")
    return when {
        os.contains("Windows", true) -> Platform.Windows
        os.contains("Mac OS", true) -> Platform.MacOS
        os.contains("Linux", true) -> Platform.Linux
        else -> {
            logger.warn("unknown os:{}", os)
            Platform.Unknown
        }
    }
}

fun protobufJsonPrinter(): JsonFormat.Printer =
    JsonFormat.printer().omittingInsignificantWhitespace().alwaysPrintFieldsWithNoPresence()

fun unixTimestamp() = System.currentTimeMillis()

fun invokeOnTargetMode(modes: Set<ServerMode>, block: () -> Unit) {
    if (modes.contains(GlobalEnv.serverMode)) {
        block.invoke()
    }
}

fun String.snakeCaseToCamelCase(): String = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, this)

fun String.snakeCaseToUpperCamelCase(): String = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this)

fun String.camelCaseToSnakeCase(): String = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, this)

fun String.upperCamelToLowerCamel(): String = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, this)

fun String.upperCamelToSnakeCase(): String = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, this)

fun tryCatch(logger: Logger, block: () -> Unit) {
    try {
        block()
    } catch (t: Throwable) {
        logger.error("", t)
    }
}

suspend fun tryCatchSuspend(logger: Logger, block: suspend () -> Unit): Unit {
    try {
        block()
    } catch (t: Throwable) {
        logger.error("", t)
    }
}

fun unexpectedMessage(message: Any): Nothing = error("unexpected message:$message")
