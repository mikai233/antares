package com.mikai233.buildlogic

import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

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
    fun `returns empty list when source set is missing`() {
        val entries = ScriptEntryScanner(projectDir.toFile(), "script").discover()

        assertEquals(emptyList(), entries)
    }
}
