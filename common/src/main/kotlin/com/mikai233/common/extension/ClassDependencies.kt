package com.mikai233.common.extension

import kotlin.reflect.KClass
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.isAccessible

/**
 * 获取类的所有依赖类
 * 遍历类的所有字段，递归获取该类的依赖类
 * 依赖类包括该类的所有父类，字段的类型，泛型类型，如果类型是泛型，只会判断集合类型
 * @param T 类
 * @return 依赖类列表
 */
inline fun <reified T> classDependenciesOf(): Set<KClass<*>> {
    return classDependenciesOf(T::class)
}

fun classDependenciesOf(clazz: KClass<*>): Set<KClass<*>> {
    val results: MutableSet<KClass<*>> = mutableSetOf()
    resolveClass(clazz, results)
    return results
}

private fun resolveClass(clazz: KClass<*>, results: MutableSet<KClass<*>>) {
    clazz.declaredMemberProperties.forEach { property ->
        property.isAccessible = true
        val type = property.returnType
        type.arguments.forEach { projection ->
            resolveKTypeProjection(projection, results)
        }
        val classifier = type.classifier
        if (classifier is KClass<*> && classifier !in results) {
            results.add(classifier)
            resolveClass(classifier, results)
        }
    }
    clazz.allSuperclasses.forEach { superClass ->
        resolveClass(superClass, results)
    }
    resolveCollection(clazz, results)
}

private fun resolveKTypeProjection(projection: KTypeProjection, results: MutableSet<KClass<*>>) {
    val type = projection.type
    if (type != null) {
        val classifier = type.classifier
        if (classifier is KClass<*> && classifier !in results) {
            results.add(classifier)
            resolveClass(classifier, results)
        }
        type.arguments.forEach { arg ->
            resolveKTypeProjection(arg, results)
        }
    }
}

private fun resolveCollection(clazz: KClass<*>, results: MutableSet<KClass<*>>) {
    if (clazz.isSubclassOf(Iterable::class)) {
        val type = clazz.typeParameters[0]
        if (type is KClass<*> && type !in results) {
            results.add(type)
            resolveClass(type, results)
        }
    } else if (clazz.isSubclassOf(Map::class)) {
        val keyType = clazz.typeParameters[0]
        val valueType = clazz.typeParameters[1]
        if (keyType is KClass<*> && keyType !in results) {
            results.add(keyType)
            resolveClass(keyType, results)
        }
        if (valueType is KClass<*> && valueType !in results) {
            results.add(valueType)
            resolveClass(valueType, results)
        }
    }
}