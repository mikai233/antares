package com.mikai233.common.db

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/6/1
 */
enum class Status {
    Clean,
    Set,
    Unset,
    ;

    fun isDirty(): Boolean {
        return this != Clean
    }
}
