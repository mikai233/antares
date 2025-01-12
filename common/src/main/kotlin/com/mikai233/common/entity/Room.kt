package com.mikai233.common.entity

import com.mikai233.common.db.Entity
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator

data class Room(
    @Id
    val id: Int,
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
) : Entity {
    companion object {
        @JvmStatic
        @PersistenceCreator
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
                Cat("", 0)
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
