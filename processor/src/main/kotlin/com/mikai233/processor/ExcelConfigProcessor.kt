package com.mikai233.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.mikai233.common.excel.ExcelConfig
import com.mikai233.common.excel.ExcelRow
import com.mikai233.common.excel.SerdeConfig
import com.mikai233.common.excel.SerdeRow
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName

class ExcelConfigProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
    private val kotlinVersion: KotlinVersion,
) : SymbolProcessor {
    private val excelRowClassName = ExcelRow::class.asClassName()
    private val configClassName = ExcelConfig::class.asClassName()
    private val rowsDef = mutableSetOf<ClassName>()
    private val configDef = mutableSetOf<ClassName>()

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getDeclarationsFromPackage("com.mikai233.shared.excel")
            .filterIsInstance<KSClassDeclaration>()
            .forEach { ksClassDeclaration ->
                ksClassDeclaration.getAllSuperTypes().forEach {
                    when (it.toClassName()) {
                        excelRowClassName -> {
                            rowsDef.add(ksClassDeclaration.toClassName())
                        }

                        configClassName -> {
                            configDef.add(ksClassDeclaration.toClassName())
                        }
                    }
                }
            }
        return emptyList()
    }

    override fun finish() {
        val serializersModule = MemberName("kotlinx.serialization.modules", "SerializersModule")
        val className = ClassName("kotlinx.serialization.modules", "SerializersModule")
        val codeBlock = buildCodeBlock {
            beginControlFlow("%M", serializersModule)
            add(genPolymorphic(rowsDef, configDef))
            endControlFlow()
        }
        val propertySpec = PropertySpec.builder("configSerdeModule", className)
            .initializer(codeBlock)
            .build()
        val fileSpec = FileSpec.builder("com.mikai233.shared.excel", "ExcelSerde")
            .addProperty(propertySpec)
            .build()
        codeGenerator.createNewFile(
            Dependencies(false), "com.mikai233.shared.excel", "ExcelSerde"
        ).use { os ->
            os.writer().use {
                fileSpec.writeTo(it)
            }
        }
    }

    private fun genPolymorphic(serdeRows: Set<ClassName>, serdeConfigs: Set<ClassName>): CodeBlock {
        val polymorphic = MemberName("kotlinx.serialization.modules", "polymorphic", true)
        val subclass = MemberName("kotlinx.serialization.modules", "subclass", true)
        return buildCodeBlock {
            beginControlFlow("%M(%T::class)", polymorphic, typeNameOf<SerdeRow>())
            serdeRows.forEach { rowClass ->
                addStatement("%M(%T::class)", subclass, rowClass)
            }
            endControlFlow()
            beginControlFlow("%M(%T::class)", polymorphic, typeNameOf<SerdeConfig>())
            serdeConfigs.forEach { rowClass ->
                addStatement("%M(%T::class)", subclass, rowClass)
            }
            endControlFlow()
        }
    }
}
