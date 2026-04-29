package com.mikai233.common.test.db.tracked.generated

import com.mikai233.common.db.Entity
import com.mikai233.common.db.tracked.TrackEntity
import org.springframework.data.annotation.Id
import java.util.*

@TrackEntity
data class GeneratedProfile(
    @Id
    val id: String,
    val account: String,
    var nickname: String,
    var title: String?,
    var level: Int,
    val settings: GeneratedProfileSettings,
    val tags: MutableList<String>,
    var optionalTags: MutableList<String>?,
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
                title = null,
                level = 1,
                settings = GeneratedProfileSettings(
                    mode = "story",
                    volume = 3,
                    notes = mutableListOf("quiet"),
                ),
                tags = mutableListOf("pve"),
                optionalTags = mutableListOf("solo"),
                attrs = linkedMapOf("power" to 10),
                flags = linkedSetOf("newbie"),
                pending = ArrayDeque(listOf(1, 2)),
                scores = intArrayOf(3, 4),
            )
        }
    }
}

data class GeneratedProfileSettings(
    val mode: String,
    var volume: Int,
    val notes: MutableList<String>,
)
