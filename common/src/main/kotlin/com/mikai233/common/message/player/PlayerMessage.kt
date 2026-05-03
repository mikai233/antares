package com.mikai233.common.message.player

import org.apache.pekko.actor.NotInfluenceReceiveTimeout

data object HandoffPlayer

data object PlayerInitialized

data object PlayerUnloaded

data object PlayerTick : NotInfluenceReceiveTimeout
