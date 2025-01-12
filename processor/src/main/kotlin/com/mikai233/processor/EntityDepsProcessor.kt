package com.mikai233.processor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import kotlin.reflect.KClass

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/3
 * 生成Entity的类型依赖关系，用于Kryo序列化反序列化时的类型注册
 */
class EntityDepsProcessor(private val codeGenerator: CodeGenerator, private val logger: KSPLogger) : SymbolProcessor {
    private val kryoSupportTypes = setOf(
        "kotlin.Int", "kotlin.Long", "kotlin.Float", "kotlin.Double",
        "kotlin.Boolean", "kotlin.Char", "kotlin.String", "kotlin.Byte",
        "kotlin.Short", "kotlin.Unit"
    )

    private val sources: MutableList<KSFile> = mutableListOf()

    private val ksClassDeclarations = mutableSetOf<KSClassDeclaration>()
    private val visitedDeclarations = mutableSetOf<KSClassDeclaration>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getAllFiles().filter { it.packageName.asString().startsWith("com.mikai233.common.entity") }
            .forEach { ksFile ->
                sources.add(ksFile)
                val entityKSClassDeclarations =
                    ksFile.declarations.filterIsInstance<KSClassDeclaration>().filter { ksClassDeclaration ->
                        ksClassDeclaration.isSubclassOfInterface("com.mikai233.common.db.Entity")
                    }
                entityKSClassDeclarations.forEach { ksClassDeclaration ->
                    collectKSClassDeclaration(ksClassDeclaration)
                }
            }
        return emptyList()
    }

    override fun finish() {
        val maxSizePerFile = 1000 // 每个文件最大类数量
        val entityDepsType =
            Array::class.asClassName().parameterizedBy(KClass::class.asClassName().parameterizedBy(STAR))

        // 拆分为多个分片
        val partitions = ksClassDeclarations.sortedBy { it.qualifiedName?.asString() ?: "" }.chunked(maxSizePerFile)

        val fileNames = mutableListOf<String>()

        // 生成拆分文件
        partitions.forEachIndexed { index, chunk ->
            val fileName = "EntityDeps$index"
            fileNames.add(fileName)

            val partitionFile = FileSpec.builder("com.mikai233.common.entity", fileName)
                .addProperty(
                    PropertySpec.builder(fileName, entityDepsType)
                        .initializer(buildCodeBlock {
                            add("arrayOf(\n")
                            chunk.forEachIndexed { chunkIndex, declaration ->
                                add("%T::class", declaration.toClassName())
                                if (chunkIndex != chunk.size - 1) {
                                    add(",\n")
                                }
                            }
                            add("\n)")
                        })
                        .build()
                )
                .build()

            // 写入拆分文件
            codeGenerator.createNewFile(
                Dependencies(true, *sources.toTypedArray()),
                "com.mikai233.common.entity",
                fileName
            ).use { os ->
                os.writer().use {
                    partitionFile.writeTo(it)
                }
            }
        }

        // 生成统一的 EntityDeps 文件
        val combinedFile = FileSpec.builder("com.mikai233.common.entity", "EntityDeps")
            .addProperty(
                PropertySpec.builder("EntityDeps", entityDepsType)
                    .initializer(buildCodeBlock {
                        add("arrayOf<Array<KClass<*>>>(\n")
                        fileNames.forEachIndexed { fileIndex, fileName ->
                            add("%N", fileName)
                            if (fileIndex != fileNames.size - 1) {
                                add(",\n")
                            }
                        }
                        add("\n).flatten().toTypedArray()") // 合并所有数组为一个数组
                    })
                    .build()
            )
            .build()

        // 写入统一的 EntityDeps 文件
        codeGenerator.createNewFile(
            Dependencies(true, *sources.toTypedArray()),
            "com.mikai233.common.entity",
            "EntityDeps"
        ).use { os ->
            os.writer().use {
                combinedFile.writeTo(it)
            }
        }
    }


    // 递归方法收集字段类型
    private fun collectKSType(kType: KSType) {
        // 如果是Kryo支持的类型，直接返回
        if (isKryoSupportType(kType)) {
            return
        }

        // 如果是泛型类型，处理泛型
        if (kType.arguments.isNotEmpty()) {
            kType.arguments.forEach { argument ->
                // 递归查找泛型的类型
                argument.type?.resolve()?.let { type ->
                    collectKSType(type)
                }
            }
        }

        val declaration = kType.declaration
        // 如果是类类型，递归查找类的字段
        if (declaration is KSClassDeclaration && declaration !in visitedDeclarations) {
            visitedDeclarations.add(declaration)
//            f.appendText("${declaration.qualifiedName?.asString()}\n")
            collectKSClassDeclaration(declaration)
        } else if (declaration is KSTypeAlias) {
            val ksTypeAlias = kType.declaration as KSTypeAlias
            collectKSType(ksTypeAlias.type.resolve())
        }
    }

    private fun collectKSClassDeclaration(
        ksClassDeclaration: KSClassDeclaration,
    ) {
        if (!ksClassDeclaration.isAbstract()) {
            if (!ksClassDeclaration.isPublic()) {
                error("Class declaration ${ksClassDeclaration.qualifiedName?.asString()} is not public, use simple type in your entity instead")
            }
            ksClassDeclarations.add(ksClassDeclaration) // 将类本身加入
        }
        val isIterable = ksClassDeclaration.isSubclassOfInterface("kotlin.collections.Iterable")
        val isMap = ksClassDeclaration.isSubclassOfInterface("kotlin.collections.Map")
        if (!(isIterable || isMap)) {
            ksClassDeclaration.getDeclaredProperties().forEach { ksProperty ->
                // 如果字段有 @Transient 注解，跳过该字段
                if (ksProperty.annotations.any { it.shortName.asString() == "Transient" }) {
                    return@forEach
                }
                ksProperty.type.resolve().let { propertyType ->
                    collectKSType(propertyType)
                }
            }
        }
    }

    // 判断是否为Kryo支持的类型
    private fun isKryoSupportType(kType: KSType): Boolean {
        return kryoSupportTypes.contains(kType.declaration.qualifiedName?.asString())
    }

    private fun KSClassDeclaration.isSubclassOfInterface(interfaceName: String): Boolean {
        // 获取所有超类型（包括接口）
        return getAllSuperTypes().any { superType ->
            superType.declaration.qualifiedName?.asString() == interfaceName
        }
    }
}