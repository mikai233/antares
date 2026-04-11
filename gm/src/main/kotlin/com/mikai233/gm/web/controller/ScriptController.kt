package com.mikai233.gm.web.controller

import com.mikai233.common.annotation.AllOpen
import com.mikai233.gm.web.dto.ScriptExecutionResponse
import com.mikai233.gm.web.service.ScriptService
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@Validated
@AllOpen
@RestController
@RequestMapping("/script")
class ScriptController(private val scriptService: ScriptService) {
    @PostMapping("/player_actor_script", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun executePlayerActorScript(
        @RequestPart("script") script: MultipartFile,
        @RequestPart("extra", required = false) extra: MultipartFile?,
        @RequestParam("player_id") playerIds: String,
    ): List<ScriptExecutionResponse> {
        return scriptService.executePlayerActorScript(script, extra, playerIds)
    }

    @PostMapping("/world_actor_script", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun executeWorldActorScript(
        @RequestPart("script") script: MultipartFile,
        @RequestPart("extra", required = false) extra: MultipartFile?,
        @RequestParam("world_id") worldIds: String,
    ): List<ScriptExecutionResponse> {
        return scriptService.executeWorldActorScript(script, extra, worldIds)
    }

    @PostMapping("/global_actor_script", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun executeGlobalActorScript(
        @RequestPart("script") script: MultipartFile,
        @RequestPart("extra", required = false) extra: MultipartFile?,
        @RequestParam("actor_name") actorName: String,
    ): ScriptExecutionResponse {
        return scriptService.executeGlobalActorScript(script, extra, actorName)
    }

    @PostMapping("/channel_actor_script", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun executeActorScriptByPath(
        @RequestPart("script") script: MultipartFile,
        @RequestPart("extra", required = false) extra: MultipartFile?,
        @RequestParam("actor_path") actorPath: String,
    ): ScriptExecutionResponse {
        return scriptService.executeActorScriptByPath(script, extra, actorPath)
    }

    @PostMapping("/node_script", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun executeNodeScript(
        @RequestPart("script") script: MultipartFile,
        @RequestPart("extra", required = false) extra: MultipartFile?,
        @RequestParam("address", required = false) addresses: List<String>?,
    ) {
        scriptService.executeNodeScript(script, extra, addresses.orEmpty())
    }

    @PostMapping("/node_role_script", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun executeNodeRoleScript(
        @RequestPart("script") script: MultipartFile,
        @RequestPart("extra", required = false) extra: MultipartFile?,
        @RequestParam("role") role: String,
        @RequestParam("address", required = false) addresses: List<String>?,
        @RequestParam("patch", required = false) patch: String?,
    ) {
        scriptService.executeNodeRoleScript(script, extra, role, addresses.orEmpty(), patch != null)
    }
}
