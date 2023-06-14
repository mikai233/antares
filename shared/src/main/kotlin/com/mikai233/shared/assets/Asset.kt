package com.mikai233.shared.assets

import com.google.common.math.LongMath
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

interface Asset {
    val id: Int
    val num: Long
    val type: AssetType

    fun operable(other: Asset) = other.id == id && other.type == type

    fun operableCheck(other: Asset) = check(operable(other)) { "$other cannot calculate with $this" }

    fun new(num: Long): Asset

    operator fun plus(other: Asset): Asset {
        operableCheck(other)
        return new(LongMath.checkedAdd(num, other.num))
    }

    operator fun minus(other: Asset): Asset {
        operableCheck(other)
        return new(LongMath.checkedSubtract(num, other.num))
    }

    operator fun compareTo(other: Asset): Int {
        operableCheck(other)
        return num.compareTo(other.num)
    }

    operator fun rem(other: Asset): Asset {
        operableCheck(other)
        return new(num % other.num)
    }

    operator fun times(scale: Long): Asset {
        return new(LongMath.checkedMultiply(this.num, scale))
    }

    operator fun times(scale: Int): Asset {
        return new(LongMath.checkedMultiply(this.num, scale.toLong()))
    }

    fun times(scale: Double, strategy: RoundingStrategy): Asset {
        val num = when (strategy) {
            RoundingStrategy.Floor -> {
                floor(num * scale)
            }

            RoundingStrategy.Ceil -> {
                ceil(num * scale)
            }

            RoundingStrategy.Round -> {
                round(num * scale)
            }
        }
        return new(num.toLong())
    }

    operator fun div(num: Int): Asset {
        return new(this.num / num)
    }
}
