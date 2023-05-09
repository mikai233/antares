package com.mikai233.core

import akka.actor.typed.ActorSystem
import com.mikai233.ext.logger

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/10
 */
open class Cluster<T> {
    val logger = logger()

    lateinit var system: ActorSystem<T>
}