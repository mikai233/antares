package com.mikai233.common.annotation

/**
 * 用于标记 [com.mikai233.common.message.Message] 是本地消息，不需要使用 Kryo 进行序列化
 * Kryo 的序列化是自动扫描的，某些本地消息可能含有序列化不支持的类型，使用此注解跳过
 */
annotation class Local
