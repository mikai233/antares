package com.mikai233.common.annotation

import akka.actor.typed.javadsl.AbstractBehavior
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
annotation class GenerateLoader(val actor: KClass<out AbstractBehavior<*>>)
