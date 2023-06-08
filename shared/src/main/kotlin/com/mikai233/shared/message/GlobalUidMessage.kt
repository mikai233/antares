package com.mikai233.shared.message

import akka.actor.typed.ActorRef

data class RetrieveWorldUidPrefixReq(val worldId: Long, val replyTo: ActorRef<RetrieveWorldUidPrefixResp>) :
    BusinessSerdeGlobalUidMessage

data class RetrieveWorldUidPrefixResp(val uuidPrefix: Long)
