package com.mikai233.shared.message

import akka.actor.typed.ActorRef
import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.core.components.Role
import com.mikai233.shared.script.ScriptType

@AllOpen
sealed class Script(val name: String, val type: ScriptType, val body: ByteArray) : SerdeScriptMessage

class NodeRoleScript(
    override val name: String,
    override val type: ScriptType,
    override val body: ByteArray,
    val role: Role
) : Script(name, type, body) {
    override fun toString(): String {
        return "NodeRoleScript(name='$name', type=$type, role=$role)"
    }
}

class NodeScript(override val name: String, override val type: ScriptType, override val body: ByteArray) :
    Script(name, type, body) {
    override fun toString(): String {
        return "NodeScript(name='$name', type=$type)"
    }
}

class PlayerActorScript(
    override val name: String,
    override val type: ScriptType,
    override val body: ByteArray,
    val replyTo: ActorRef<PlayerMessage>
) : Script(name, type, body) {
    override fun toString(): String {
        return "PlayerActorScript(name='$name', type=$type, replyTo=$replyTo)"
    }
}

class WorldActorScript(
    override val name: String,
    override val type: ScriptType,
    override val body: ByteArray,
    val replyTo: ActorRef<WorldMessage>
) :
    Script(name, type, body) {
    override fun toString(): String {
        return "WorldActorScript(name='$name', type=$type, replyTo=$replyTo)"
    }
}