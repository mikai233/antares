package com.mikai233.gm.web.models

import com.mikai233.common.script.ScriptType

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/8
 */
class ScriptPart(val name: String, val type: ScriptType, val body: ByteArray) {
    override fun toString(): String {
        return "ScriptPart1(name='$name', type=$type)"
    }
}
