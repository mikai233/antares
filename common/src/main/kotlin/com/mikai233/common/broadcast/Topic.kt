package com.mikai233.common.broadcast

object Topic {
    const val All_WORLDS_TOPIC = "all_worlds_topic"

    const val CROSS_WORLD_CHAT = "cross_world_chat_topic"

    const val WORLD_ACTIVE = "world_active"

    fun ofWorld(wordId: Long): String = "world_${wordId}_topic"

    fun ofAlliance(allianceId: Long): String = "alliance_${allianceId}_topic"
}
