package com.mikai233.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/3
 */
class EntityDepsProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return EntityDepsProcessor(environment.codeGenerator)
    }
}
