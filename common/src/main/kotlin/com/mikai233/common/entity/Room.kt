package com.mikai233.common.entity

import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.springframework.data.annotation.Id

data class Room(
    @Id
    val id: Int,
    val name: String,
    val createTime: Long,
    var changeableBoolean: Boolean,
    var changeableString: String,
    val players: HashMap<Int, Player>,
    val directObj: DirectObj,
    var listObj: MutableList<String>?,
    val trackChild: TrackChild,
    var animals: MutableList<Animal>,
    var directInterface: Animal,
) : TraceableFieldEntity<Int> {
    override fun key(): Int {
        return id
    }
}


@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
interface Player

data class RoomPlayer(val id: Int, var level: Int) : Player

data class DirectObj(val a: String, var b: Int, var c: Long, var d: Boolean)

data class TrackChild(val a: String, var b: String)


@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
interface Animal

data class Cat(val name: String, val age: Int) : Animal

data class Bird(val name: String) : Animal
