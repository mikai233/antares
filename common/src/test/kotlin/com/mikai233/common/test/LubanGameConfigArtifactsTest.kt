package com.mikai233.common.test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LubanGameConfigArtifactsTest {
    @Test
    fun exportedArtifactsExposeSingleBundleArtifact() {
        val files = LubanTestConfigArtifacts.artifacts()

        assertEquals(1, files.size)
        assertEquals(LubanTestConfigArtifacts.BUNDLE_FILE, files.single().relativePath)
        assertTrue(files.single().bytes.isNotEmpty())
    }

    @Test
    fun bundleContainsAllPublishedBinaryFiles() {
        val filesByPath = LubanTestConfigArtifacts.unpackBundle(LubanTestConfigArtifacts.bundleBytes())

        assertEquals(
            listOf(
                "game_tbitem.bytes",
                "game_tbmonster.bytes",
                "game_tbdroppool.bytes",
                "game_tbscene.bytes",
                "game_tbactivity.bytes",
            ).sorted(),
            filesByPath.keys.sorted(),
        )
        assertTrue(filesByPath.values.all { it.isNotEmpty() })
    }
}
