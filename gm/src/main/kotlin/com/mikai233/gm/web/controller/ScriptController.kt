package com.mikai233.gm.web.controller

import com.mikai233.common.annotation.AllOpen
import com.mikai233.gm.script.ScriptExecutionView
import com.mikai233.gm.web.dto.CreateScriptExecutionRequest
import com.mikai233.gm.web.service.ScriptService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@Validated
@AllOpen
@RestController
@RequestMapping("/script")
class ScriptController(private val scriptService: ScriptService) {
    @PostMapping("/executions", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.ACCEPTED)
    suspend fun createExecution(
        @RequestPart("script") script: MultipartFile,
        @RequestPart("extra", required = false) extra: MultipartFile?,
        @RequestPart("request") request: CreateScriptExecutionRequest,
    ): ScriptExecutionView {
        return scriptService.createExecution(script, extra, request)
    }

    @GetMapping("/executions")
    suspend fun listExecutions(): List<ScriptExecutionView> {
        return scriptService.listExecutions()
    }

    @GetMapping("/executions/{id}")
    suspend fun getExecution(@PathVariable id: String): ScriptExecutionView {
        return scriptService.getExecution(id)
    }
}
