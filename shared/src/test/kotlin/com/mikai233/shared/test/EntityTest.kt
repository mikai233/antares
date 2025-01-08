package com.mikai233.shared.test

import com.mikai233.common.db.Entity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.reflections.Reflections
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.core.mapping.Document
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
        Reflections("com.mikai233.shared.entity").getSubTypesOf(Entity::class.java).forEach { entityClass ->
            val entityKClass = entityClass.kotlin
            // 检查是否有 @Document 注解
            val isDocumentAnnotationPresent = entityClass.isAnnotationPresent(Document::class.java)
            assertNotNull(
                isDocumentAnnotationPresent,
                "Class[${entityKClass.qualifiedName}] must have @Document annotation with snake_case collection name"
            )
            val documentAnnotation = entityClass.getAnnotation(Document::class.java)
            assertTrue(
                documentAnnotation.collection.isNotBlank(),
                "Class[${entityKClass.qualifiedName}] must have @Document annotation with snake_case collection name"
            )
            // 检查是否有属性带 @Id 注解
            val isIdAnnotationPresent =
                entityKClass.declaredMemberProperties.any { it.javaField?.isAnnotationPresent(Id::class.java) == true }
            assertTrue(
                isIdAnnotationPresent,
                "Class[${entityKClass.qualifiedName}] must have a property with @Id annotation"
            )

            // 检查是否有伴生对象
            val companionObject = entityKClass.companionObject
            assertNotNull(companionObject, "Class[${entityKClass.qualifiedName}] must have a companion object")

            // 检查伴生对象中是否有 create 方法
            val createMethod = companionObject!!.declaredFunctions.find { it.name == "create" }
            assertNotNull(
                createMethod,
                "Class[${entityKClass.qualifiedName}] Companion object must have a create method"
            )
            assertTrue(
                createMethod?.javaMethod?.annotations?.any { it is PersistenceCreator } == true,
                "Class[${entityKClass.qualifiedName}] Create method must have @PersistenceCreator annotation"
            )
            assertTrue(
                createMethod!!.hasAnnotation<JvmStatic>(),
                "Class[${entityKClass.qualifiedName}] Create method must have @JvmStatic annotation"
            )
            assertTrue(
                createMethod.parameters.size == 1,
                "Class[${entityKClass.qualifiedName}] Create method must have no parameters"
            )

            // 调用 create 方法并检查返回值是否为目标类的实例
            assertEquals(
                createMethod.returnType.jvmErasure,
                entityKClass,
                "Class[${entityKClass.qualifiedName}] Create method must return an instance of the target class"
            )
        }
    }
}