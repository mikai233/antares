package com.mikai233.common.entity

import io.github.realmlabs.asteria.persistence.Entity

data class Room(
    override val id: Int,
    val name: String,
    val createTime: Long,
    var changeableBoolean: Boolean,
    var changeableString: String,
    val players: HashMap<Int, TPlayer>,
    val directObj: DirectObj,
    var listObj: MutableList<String>?,
    val trackChild: TrackChild,
    var animals: MutableList<Animal>,
    var directInterface: Animal,
) : Entity<Int> {
    companion object {
        @JvmStatic
        fun create(): Room {
            return Room(
                0,
                "",
                0,
                false,
                "",
                hashMapOf(),
                DirectObj("", 0, 0, false),
                null,
                TrackChild("", "cc"),
                mutableListOf(),
                Cat("", 0),
            )
        }
    }
}


interface TPlayer

data class RoomPlayer(val id: Int, var level: Int) : TPlayer

data class DirectObj(val a: String, var b: Int, var c: Long, var d: Boolean)

data class TrackChild(val a: String, var b: String)

interface Animal

data class Cat(val name: String, val age: Int) : Animal

data class Bird(val name: String) : Animal
