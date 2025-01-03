package com.mikai233.processor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isAbstract
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

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getAllFiles().filter { it.packageName.asString() == "com.mikai233.shared.entity" }.forEach { ksFile ->
            sources.add(ksFile)
            val entityKSClassDeclarations =
                ksFile.declarations.filterIsInstance<KSClassDeclaration>().filter { ksClassDeclaration ->
                    ksClassDeclaration.isSubclassOfInterface("com.mikai233.common.db.Entity")
                }
            entityKSClassDeclarations.forEach { ksClassDeclaration ->
                collectKSClassDeclaration(ksClassDeclaration, ksClassDeclarations)
            }
        }
        return emptyList()
    }

    override fun finish() {
        val entityDepsType =
            Array::class.asClassName().parameterizedBy(KClass::class.asClassName().parameterizedBy(STAR))
        val entityDepsFile = FileSpec.builder("com.mikai233.shared.entity", "EntityDeps")
            .addProperty(
                PropertySpec.builder("EntityDeps", entityDepsType)
                    .initializer(buildCodeBlock {
                        add("arrayOf(\n")
                        ksClassDeclarations.forEachIndexed { index, declaration ->
                            add("%T::class", declaration.toClassName())
                            if (index != ksClassDeclarations.size - 1) {
                                add(",\n")
                            }
                        }
                        add("\n)")
                    })
                    .build()
            )
            .build()
        codeGenerator.createNewFile(
            Dependencies(true, *sources.toTypedArray()),
            "com.mikai233.shared.entity",
            "EntityDeps"
        ).use { os ->
            os.writer().use {
                entityDepsFile.writeTo(it)
            }
        }
    }

    // 递归方法收集字段类型
    private fun collectKSType(kType: KSType, collectedTypes: MutableSet<KSClassDeclaration>) {
        // 如果是Kryo支持的类型，直接返回
        if (isKryoSupportType(kType)) {
            return
        }

        // 如果是泛型类型，处理泛型
        if (kType.arguments.isNotEmpty()) {
            kType.arguments.forEach { argument ->
                // 递归查找泛型的类型
                argument.type?.resolve()?.let { type ->
                    collectKSType(type, collectedTypes)
                }
            }
        }

        // 如果是类类型，递归查找类的字段
        if (kType.declaration is KSClassDeclaration) {
            collectKSClassDeclaration(kType.declaration as KSClassDeclaration, collectedTypes)
        } else if (kType.declaration is KSTypeAlias) {
            val ksTypeAlias = kType.declaration as KSTypeAlias
            collectKSType(ksTypeAlias.type.resolve(), collectedTypes)
        }
    }

    private fun collectKSClassDeclaration(
        ksClassDeclaration: KSClassDeclaration,
        collectedTypes: MutableSet<KSClassDeclaration>
    ) {
        if (!ksClassDeclaration.isAbstract()) {
            collectedTypes.add(ksClassDeclaration) // 将类本身加入
        }
        val isIterable = ksClassDeclaration.isSubclassOfInterface("kotlin.collections.Iterable")
        val isMap = ksClassDeclaration.isSubclassOfInterface("kotlin.collections.Map")
        if (!(isIterable || isMap)) {
            ksClassDeclaration.getDeclaredProperties().forEach { ksProperty ->
                logger.warn("ppv${ksProperty.qualifiedName?.asString()} for ${ksClassDeclaration.qualifiedName?.asString()}")
                // 如果字段有 @Transient 注解，跳过该字段
                if (ksProperty.annotations.any { it.shortName.asString() == "Transient" }) {
                    return@forEach
                }
                ksProperty.type.resolve().let { propertyType ->
                    collectKSType(propertyType, collectedTypes)
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