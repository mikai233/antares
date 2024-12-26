package com.mikai233.shared.message


data class RetrieveWorldUidPrefixReq(val worldId: Long) : GlobalUidMessage

data class RetrieveWorldUidPrefixResp(val uuidPrefix: Long)
