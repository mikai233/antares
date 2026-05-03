package com.mikai233.common.test

import io.github.realmlabs.asteria.persistence.Entity
import io.github.realmlabs.asteria.persistence.mongodb.annotations.AsteriaMongoEntity
import io.github.realmlabs.asteria.persistence.mongodb.annotations.AsteriaMongoId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.reflections.Reflections
import java.lang.reflect.Modifier
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmErasure

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/8
 */
class EntityTest {

    @Test
    fun testEntity() {
        Reflections("com.mikai233.common.entity").getSubTypesOf(Entity::class.java)
            .filter { entityClass ->
                entityClass.`package`.name == "com.mikai233.common.entity" &&
                    !entityClass.isInterface &&
                    !Modifier.isAbstract(entityClass.modifiers)
            }
            .forEach { entityClass ->
                val entityKClass = entityClass.kotlin
                val asteriaMongoEntity = entityClass.getAnnotation(AsteriaMongoEntity::class.java)
                if (asteriaMongoEntity != null) {
                    assertTrue(
                        asteriaMongoEntity.collection.isNotBlank(),
                        "Class[${entityKClass.qualifiedName}] must declare a non-blank Asteria mongo collection",
                    )
                    val hasMongoId =
                        entityKClass.declaredMemberProperties.any {
                            it.name == "id" || it.javaField?.isAnnotationPresent(AsteriaMongoId::class.java) == true
                        }
                    assertTrue(
                        hasMongoId,
                        "Class[${entityKClass.qualifiedName}] must expose an id property or @AsteriaMongoId",
                    )
                }

                // 检查是否有伴生对象
                val companionObject = entityKClass.companionObject
                assertNotNull(companionObject, "Class[${entityKClass.qualifiedName}] must have a companion object")

                // 检查伴生对象中是否有 create 方法
                val createMethod = companionObject!!.declaredFunctions.find { it.name == "create" }
                assertNotNull(
                    createMethod,
                    "Class[${entityKClass.qualifiedName}] Companion object must have a create method",
                )
                assertTrue(
                    createMethod!!.hasAnnotation<JvmStatic>(),
                    "Class[${entityKClass.qualifiedName}] Create method must have @JvmStatic annotation",
                )
                assertTrue(
                    createMethod.parameters.size == 1,
                    "Class[${entityKClass.qualifiedName}] Create method must have no parameters",
                )

                // 调用 create 方法并检查返回值是否为目标类的实例
                assertEquals(
                    createMethod.returnType.jvmErasure,
                    entityKClass,
                    "Class[${entityKClass.qualifiedName}] Create method must return an instance of the target class",
                )
            }
    }
}
