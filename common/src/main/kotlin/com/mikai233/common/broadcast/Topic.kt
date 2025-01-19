package com.mikai233.common.broadcast

object Topic {
    const val All_WORLDS_TOPIC = "all_worlds_topic"

    const val WORLD_ACTIVE = "world_active"

    fun ofWorld(wordId: Long): String = "world_${wordId}_topic"
}
