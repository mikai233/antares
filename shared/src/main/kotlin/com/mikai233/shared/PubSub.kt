package com.mikai233.shared

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.pubsub.Topic
import com.mikai233.shared.message.AllWorldTopicMessage
import com.mikai233.shared.message.WorldTopicMessage

fun ActorContext<*>.startWorldTopicActor(worldId: Long): ActorRef<Topic.Command<WorldTopicMessage>> {
    return spawnAnonymous(Topic.create(WorldTopicMessage::class.java, "topicOfWorld$worldId"))
}

fun ActorContext<*>.startAllWorldTopicActor(): ActorRef<Topic.Command<AllWorldTopicMessage>> {
    return spawnAnonymous(Topic.create(AllWorldTopicMessage::class.java, "topicOfAllWorld"))
}
