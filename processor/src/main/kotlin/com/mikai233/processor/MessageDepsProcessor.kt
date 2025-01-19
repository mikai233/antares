package com.mikai233.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import kotlin.reflect.KClass

class MessageDepsProcessor(private val codeGenerator: CodeGenerator) : SymbolProcessor {
    private val sources: MutableSet<KSFile> = mutableSetOf()

    private val targetDeclarations = mutableSetOf<KSClassDeclaration>()
    private val visitedDeclarations = mutableSetOf<KSClassDeclaration>()
    private val ignoreQualifiedNames: MutableSet<String> = mutableSetOf("com.google.protobuf.GeneratedMessage")

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getAllFiles().forEach { ksFile ->
            val entityKSClassDeclarations =
                ksFile.declarations.filterIsInstance<KSClassDeclaration>().filter { ksClassDeclaration ->
                    ksClassDeclaration.isSubclassOf("com.mikai233.common.message.Message")
                }
            entityKSClassDeclarations.forEach { ksClassDeclaration ->
                sources.add(ksFile)
                collectKSClassDeclaration(
                    ksClassDeclaration,
                    visitedDeclarations,
                    targetDeclarations,
                    ignoreQualifiedNames,
                )
            }
        }
        return emptyList()
    }

    override fun finish() {
        val maxSizePerFile = 1000 // 每个文件最大类数量
        val entityDepsType =
            Array::class.asClassName().parameterizedBy(KClass::class.asClassName().parameterizedBy(STAR))

        // 拆分为多个分片
        val partitions = targetDeclarations.sortedBy { it.qualifiedName?.asString() ?: "" }.chunked(maxSizePerFile)

        val fileNames = mutableListOf<String>()

        // 生成拆分文件
        partitions.forEachIndexed { index, chunk ->
            val fileName = "MessageDeps$index"
            fileNames.add(fileName)

            val partitionFile = FileSpec.builder("com.mikai233.common.message", fileName)
                .addProperty(
                    PropertySpec.builder(fileName, entityDepsType)
                        .initializer(
                            buildCodeBlock {
                                add("arrayOf(\n")
                                chunk.forEachIndexed { chunkIndex, declaration ->
                                    add("%T::class", declaration.toClassName())
                                    if (chunkIndex != chunk.size - 1) {
                                        add(",\n")
                                    }
                                }
                                add("\n)")
                            },
                        )
                        .build(),
                )
                .build()

            // 写入拆分文件
            @Suppress("SpreadOperator")
            codeGenerator.createNewFile(
                Dependencies(true, *sources.toTypedArray()),
                "com.mikai233.common.message",
                fileName,
            ).use { os ->
                os.writer().use {
                    partitionFile.writeTo(it)
                }
            }
        }

        // 生成统一的 MessageDeps 文件
        val combinedFile = FileSpec.builder("com.mikai233.common.message", "MessageDeps")
            .addProperty(
                PropertySpec.builder("MessageDeps", entityDepsType)
                    .initializer(
                        buildCodeBlock {
                            add("arrayOf<Array<KClass<*>>>(\n")
                            fileNames.forEachIndexed { fileIndex, fileName ->
                                add("%N", fileName)
                                if (fileIndex != fileNames.size - 1) {
                                    add(",\n")
                                }
                            }
                            add("\n).flatten().toTypedArray()") // 合并所有数组为一个数组
                        },
                    )
                    .build(),
            )
            .build()

        // 写入统一的 EntityDeps 文件
        @Suppress("SpreadOperator")
        codeGenerator.createNewFile(
            Dependencies(true, *sources.toTypedArray()),
            "com.mikai233.common.message",
            "MessageDeps",
        ).use { os ->
            os.writer().use {
                combinedFile.writeTo(it)
            }
        }
    }
}
