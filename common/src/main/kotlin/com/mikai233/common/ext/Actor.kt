package com.mikai233.common.ext

import akka.actor.typed.javadsl.AbstractBehavior
import org.slf4j.Logger

inline fun <reified T> AbstractBehavior<T>.actorLogger(): Logger {
    return context.log
}