package com.mikai233.common.message.player

import org.apache.pekko.actor.NotInfluenceReceiveTimeout

data object HandoffPlayer

data object PlayerTick : NotInfluenceReceiveTimeout
