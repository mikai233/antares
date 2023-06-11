package com.mikai233.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class MessageForwardProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return MessageForwardProcessor(
            environment.codeGenerator,
            environment.logger,
            environment.options,
            environment.kotlinVersion
        )
    }
}