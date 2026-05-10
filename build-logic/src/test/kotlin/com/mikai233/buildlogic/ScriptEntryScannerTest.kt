package com.mikai233.buildlogic

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class ScriptEntryScannerTest {
    @TempDir
    lateinit var projectDir: Path

    @Test
    fun `discovers kotlin node and actor scripts`() {
        projectDir.resolve("src/script/kotlin/com/mikai233/player/script").createDirectories()
            .resolve("PlayerScripts.kt")
            .writeText(
                """
                package com.mikai233.player.script

                class LoginPatch : NodeScript<PlayerNode>()
                object ActorPatch : ActorScript<PlayerActor>()
                class Helper : LoginService()
                """.trimIndent(),
            )

        val entries = ScriptEntryScanner(projectDir.toFile(), "script").discover()

        assertEquals(
            listOf(
                ScriptEntry("ActorPatch", "com.mikai233.player.script.ActorPatch"),
                ScriptEntry("LoginPatch", "com.mikai233.player.script.LoginPatch"),
            ),
            entries,
        )
    }

    @Test
    fun `discovers groovy scripts`() {
        projectDir.resolve("src/script/groovy/com/mikai233/player/script").createDirectories()
            .resolve("TestGroovyScript.groovy")
            .writeText(
                """
                package com.mikai233.player.script

                class TestGroovyScript extends NodeScript<PlayerNode> {
                }
                """.trimIndent(),
            )

        val entries = ScriptEntryScanner(projectDir.toFile(), "script").discover()

        assertEquals(
            listOf(ScriptEntry("TestGroovyScript", "com.mikai233.player.script.TestGroovyScript")),
            entries,
        )
    }

    @Test
    fun `discovers runtime patch plugins when base type is configured`() {
        projectDir.resolve("src/patch/kotlin/com/mikai233/player/patch").createDirectories()
            .resolve("PlayerPatches.kt")
            .writeText(
                """
                package com.mikai233.player.patch

                class LoginPatch : RuntimePatchPlugin {
                }
                object RpcPatch : RuntimePatchPlugin {
                }
                class Helper : NodeScript<PlayerNode>()
                """.trimIndent(),
            )

        val entries = ScriptEntryScanner(
            projectDir = projectDir.toFile(),
            sourceSetName = "patch",
            baseTypeRegex = Regex("""\bRuntimePatchPlugin\b"""),
        ).discover()

        assertEquals(
            listOf(
                ScriptEntry("LoginPatch", "com.mikai233.player.patch.LoginPatch"),
                ScriptEntry("RpcPatch", "com.mikai233.player.patch.RpcPatch"),
            ),
            entries,
        )
    }

    @Test
    fun `script scanner ignores runtime patch plugins by default`() {
        projectDir.resolve("src/script/kotlin/com/mikai233/player/script").createDirectories()
            .resolve("MixedEntries.kt")
            .writeText(
                """
                package com.mikai233.player.script

                class LoginPatch : RuntimePatchPlugin {
                }
                class PlayerScript : NodeScript<PlayerNode>()
                """.trimIndent(),
            )

        val entries = ScriptEntryScanner(projectDir.toFile(), "script").discover()

        assertEquals(
            listOf(ScriptEntry("PlayerScript", "com.mikai233.player.script.PlayerScript")),
            entries,
        )
    }

    @Test
    fun `returns empty list when source set is missing`() {
        val entries = ScriptEntryScanner(projectDir.toFile(), "script").discover()

        assertEquals(emptyList(), entries)
    }
}
