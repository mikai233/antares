package com.mikai233.common.entity

import kotlinx.serialization.Serializable
import org.springframework.data.annotation.Id

@Serializable
data class Room(
    @Id
    val id: Int,
    val name: String,
    val createTime: Long,
    var changeableBoolean: Boolean,
    var changeableString: String,
    val players: HashMap<Int, RoomPlayer>,
    val directObj: DirectObj,
    var listObj: MutableList<String>?,
    val trackChild: TrackChild,
) : TraceableFieldEntity<Int> {
    override fun key(): Int {
        return id
    }
}

@Serializable
data class RoomPlayer(val id: Int, var level: Int)

@Serializable
data class DirectObj(val a: String, var b: Int, var c: Long, var d: Boolean)

@Serializable
data class TrackChild(val a: String, var b: String)