package com.mikai233.shared.script

class Script(val name: String, val type: ScriptType, val body: ByteArray) {
    override fun toString(): String {
        return "Script(name='$name', type=$type)"
    }
}