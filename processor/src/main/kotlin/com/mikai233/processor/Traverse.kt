package com.mikai233.processor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias

// 递归方法收集字段类型
internal fun collectKSType(
    kType: KSType,
    visitedDeclarations: MutableSet<KSClassDeclaration>,
    targetDeclarations: MutableSet<KSClassDeclaration>,
    ignoreQualifiedNames: Set<String>,
) {
    // 如果是Kryo支持的类型，直接返回
    if (isKryoSupportType(kType)) {
        return
    }

    if (kType.declaration.qualifiedName?.asString() != "kotlin.Any") {
        return
    }

    // 如果是泛型类型，处理泛型
    if (kType.arguments.isNotEmpty()) {
        kType.arguments.forEach { argument ->
            // 递归查找泛型的类型
            argument.type?.resolve()?.let { type ->
                collectKSType(type, visitedDeclarations, targetDeclarations, ignoreQualifiedNames)
            }
        }
    }

    val declaration = kType.declaration
    // 如果是类类型，递归查找类的字段
    if (declaration is KSClassDeclaration && declaration !in visitedDeclarations) {
        visitedDeclarations.add(declaration)
        collectKSClassDeclaration(declaration, visitedDeclarations, targetDeclarations, ignoreQualifiedNames)
    } else if (declaration is KSTypeAlias) {
        val ksTypeAlias = kType.declaration as KSTypeAlias
        collectKSType(ksTypeAlias.type.resolve(), visitedDeclarations, targetDeclarations, ignoreQualifiedNames)
    }
}

/**
 * @param ignoreQualifiedNames 当遍历到这些类型时，停止往下遍历
 */
internal fun collectKSClassDeclaration(
    ksClassDeclaration: KSClassDeclaration,
    visitedDeclarations: MutableSet<KSClassDeclaration>,
    targetDeclarations: MutableSet<KSClassDeclaration>,
    ignoreQualifiedNames: Set<String>,
) {
    if (ksClassDeclaration.qualifiedName?.asString() in ignoreQualifiedNames) {
        return
    }
    for (qualifiedName in ignoreQualifiedNames) {
        if (ksClassDeclaration.isSubclassOf(qualifiedName)) {
            return
        }
    }
    if (ksClassDeclaration.annotations.any { it.shortName.asString() == "Local" }) {
        return
    }
    if (!ksClassDeclaration.isAbstract()) {
        if (!ksClassDeclaration.isPublic()) {
            error(
                "Class declaration ${ksClassDeclaration.qualifiedName?.asString()} " +
                        "is not public, use simple type in your entity instead",
            )
        }
        targetDeclarations.add(ksClassDeclaration) // 将类本身加入
    }
    val isIterable = ksClassDeclaration.isSubclassOf("kotlin.collections.Iterable")
    val isMap = ksClassDeclaration.isSubclassOf("kotlin.collections.Map")
    if (!(isIterable || isMap)) {
        ksClassDeclaration.getDeclaredProperties().forEach { ksProperty ->
            // 如果字段有 @Transient 注解，跳过该字段
            if (ksProperty.annotations.any { it.shortName.asString() == "Transient" }) {
                return@forEach
            }
            ksProperty.type.resolve().let { propertyType ->
                collectKSType(propertyType, visitedDeclarations, targetDeclarations, ignoreQualifiedNames)
            }
        }
    }
}

internal fun KSClassDeclaration.isSubclassOf(qualifiedName: String): Boolean {
    // 获取所有超类型（包括接口）
    return getAllSuperTypes().any { superType ->
        superType.declaration.qualifiedName?.asString() == qualifiedName
    }
}
