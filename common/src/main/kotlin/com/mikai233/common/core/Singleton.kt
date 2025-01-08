package com.mikai233.common.core

enum class Singleton(val actorName: String) {
    Worker("worker"),
    Monitor("monitor"),
    ;

    companion object {
        fun fromActorName(actorName: String): Singleton? {
            return entries.find { it.actorName == actorName }
        }
    }
}