package com.mikai233.messageksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class MessageCatalogProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return MessageCatalogProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
        )
    }
}
