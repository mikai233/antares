package com.mikai233.gm.web.models

sealed interface ActorScript

data class PlayerActorScript(val players: List<Long>) : ActorScript

data class WorldActorScript(val worlds: List<Long>) : ActorScript

data class ChannelActorScript(val path: String) : ActorScript

data class GlobalActorScript(val type: String) : ActorScript
