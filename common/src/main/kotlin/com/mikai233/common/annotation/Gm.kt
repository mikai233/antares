package com.mikai233.common.annotation

/**
 * 用于标记 [com.mikai233.common.message.MessageHandler] 中的某个方法用来处理 Gm 指令
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Gm(val command: String)
