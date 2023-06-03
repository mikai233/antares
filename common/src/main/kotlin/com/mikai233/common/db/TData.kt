package com.mikai233.common.db

import com.google.common.hash.HashCode

data class TData(
    var inner: Any?,
    var objHash: Int,
    var serdeHash: HashCode,
    var sameObjHashCount: Int
)