package com.mikai233.ext

import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
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