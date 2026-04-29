package com.mikai233.common.test.db.tracked.generated

import com.mikai233.common.db.Entity
import com.mikai233.common.db.tracked.TrackEntity
import org.springframework.data.annotation.Id
import java.util.ArrayDeque
import java.util.Deque

@TrackEntity
data class GeneratedProfile(
    @Id
    val id: String,
    val account: String,
    var nickname: String,
    var level: Int,
    val tags: MutableList<String>,
    val attrs: MutableMap<String, Int>,
    val flags: MutableSet<String>,
    val pending: Deque<Int>,
    val scores: IntArray,
) : Entity {
    companion object {
        fun sample(): GeneratedProfile {
            return GeneratedProfile(
                id = "profile-1",
                account = "account-1",
                nickname = "old-name",
                level = 1,
                tags = mutableListOf("pve"),
                attrs = linkedMapOf("power" to 10),
                flags = linkedSetOf("newbie"),
                pending = ArrayDeque(listOf(1, 2)),
                scores = intArrayOf(3, 4),
            )
        }
    }
}
