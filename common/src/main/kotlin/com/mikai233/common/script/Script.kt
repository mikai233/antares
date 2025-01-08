package com.mikai233.common.script

/**
 * @param name 脚本名
 * @param type 脚本类型
 * @param body 脚本本体
 * @param extra 执行脚本的额外数据
 */
class Script(val name: String, val type: ScriptType, val body: ByteArray, val extra: ByteArray?) {
    override fun toString(): String {
        return "Script(name='$name', type=$type)"
    }
}
