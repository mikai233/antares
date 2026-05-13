package com.mikai233.gm.web.ops

import com.mikai233.common.annotation.AllOpen
import io.github.realmlabs.asteria.script.job.spring.ScriptJobSpringProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@AllOpen
@RestController
@RequestMapping("/gm/api/scripts/settings")
class GmScriptSettingsController(
    private val properties: ScriptJobSpringProperties,
) {
    @GetMapping
    fun settings(): GmScriptSettingsResponse {
        return GmScriptSettingsResponse(
            defaultMaxConcurrentItems = properties.maxConcurrentItems,
        )
    }
}

data class GmScriptSettingsResponse(
    val defaultMaxConcurrentItems: Int,
)
