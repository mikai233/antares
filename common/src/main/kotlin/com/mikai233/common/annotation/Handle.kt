package com.mikai233.common.annotation

import kotlin.reflect.KClass

/**
 * @param message 消息类型，当且仅当需要处理此类型的消息，但是并不关心消息里面的内容时，才使用，这个时候handle function的参数应该只有一个
 * 如果handle function的参数有两个，那么[message]无效，会使用handle function的第二个参数的类型作为消息类型
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Handle(val message: KClass<out Any> = Any::class)
