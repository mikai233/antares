package com.mikai233.gm.script

//sealed interface WaiterMessage
//
//object WaitTimeout : WaiterMessage
//
//class RecvWrap(val inner: Message) : WaiterMessage
//
//class ScriptWaiter<M, R>(
//    context: ActorContext<WaiterMessage>,
//    val total: Int,
//    timeout: Duration,
//    transformer: (List<M>) -> R
//) :
//    AbstractBehavior<WaiterMessage>(context) where M : Message {
//    private val aggregator: MutableList<M> = mutableListOf()
//
//    override fun createReceive(): Receive<WaiterMessage> {
//        return newReceiveBuilder().onAnyMessage {
//            Behaviors.withTimers {
//                Behaviors.same()
//            }
//        }.build()
//    }
//}
