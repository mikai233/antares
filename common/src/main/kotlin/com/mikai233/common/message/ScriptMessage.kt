package com.mikai233.common.message

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.Address
import com.mikai233.common.core.Role
import com.mikai233.common.script.ActorScriptFunction
import com.mikai233.common.script.Script

/**
 * Execute a script on the node with a specific role
 * @param uid unique id of the script
 * @param script script to execute
 * @param role role to execute the script
 * @param filter filter the nodes to execute the script
 */
data class ExecuteNodeRoleScript(val uid: String, val script: Script, val role: Role, val filter: Set<Address>) :
    Message

/**
 * Execute a script on the node
 * @param uid unique id of the script
 * @param script script to execute
 * @param filter filter the nodes to execute the script
 */
data class ExecuteNodeScript(val uid: String, val script: Script, val filter: Set<Address>) : Message

/**
 * Execute a script on the actor
 * @param id if actor is a shard actor, id is the shard entity id else id is not used
 * @param uid unique id of the script
 * @param script script to execute
 */
data class ExecuteActorScript(override val id: Long, val uid: String, val script: Script) : ShardMessage<Long>

/**
 * Compile a script on script actor
 * @param uid unique id of the script
 * @param script script to compile
 * @param actor actor to execute the script
 */
data class CompileActorScript(val uid: String, val script: Script, val actor: ActorRef) : Message

/**
 * Execute a function on the actor
 * @param uid unique id of the function
 * @param function function to execute
 * @param extra extra data used in function
 */
data class ExecuteActorFunction(
    val uid: String,
    val function: ActorScriptFunction<in AbstractActor>,
    val extra: ByteArray?
) : Message {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ExecuteActorFunction

        if (uid != other.uid) return false
        if (function != other.function) return false
        if (!extra.contentEquals(other.extra)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = uid.hashCode()
        result = 31 * result + function.hashCode()
        result = 31 * result + extra.contentHashCode()
        return result
    }
}

data class ExecuteScriptResult(val uid: String, val success: Boolean) : Message