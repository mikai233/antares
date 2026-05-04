package com.mikai233.common.test

import io.github.realmlabs.asteria.persistence.Entity
import io.github.realmlabs.asteria.persistence.mongodb.annotations.AsteriaMongoEntity
import io.github.realmlabs.asteria.persistence.mongodb.annotations.AsteriaMongoId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.reflections.Reflections
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.mongodb.core.mapping.Document
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaField
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
            .sortedBy { it.name }
            .forEach { entityClass ->
                val entityKClass = entityClass.kotlin
                assertMongoEntityContract(entityKClass)
                assertCreateFactoryContract(entityKClass)
            }
    }

    private fun assertMongoEntityContract(entityKClass: KClass<*>) {
        val entityName = entityKClass.qualifiedName
        val asteriaMongoEntity = entityKClass.java.getAnnotation(AsteriaMongoEntity::class.java) ?: return
        val document = entityKClass.java.getAnnotation(Document::class.java)
        assertNotNull(
            document,
            "Class[$entityName] must declare @Document when using @AsteriaMongoEntity",
        )
        assertTrue(
            asteriaMongoEntity.collection.isNotBlank(),
            "Class[$entityName] must declare a non-blank Asteria mongo collection",
        )
        assertEquals(
            asteriaMongoEntity.collection,
            document!!.collection,
            "Class[$entityName] @Document collection must match @AsteriaMongoEntity collection",
        )
        val hasMongoId =
            entityKClass.declaredMemberProperties.any {
                it.name == "id" || it.javaField?.isAnnotationPresent(AsteriaMongoId::class.java) == true
            }
        assertTrue(
            hasMongoId,
            "Class[$entityName] must expose an id property or @AsteriaMongoId",
        )
    }

    private fun assertCreateFactoryContract(entityKClass: KClass<*>) {
        val entityName = entityKClass.qualifiedName
        val companionObject = requireNotNull(entityKClass.companionObject) {
            "Class[$entityName] must have a companion object"
        }
        val createMethod = requireNotNull(companionObject.declaredFunctions.find { it.name == "create" }) {
            "Class[$entityName] Companion object must have a create method"
        }

        assertTrue(
            createMethod.hasAnnotation<JvmStatic>(),
            "Class[$entityName] Create method must have @JvmStatic annotation",
        )
        assertTrue(
            createMethod.hasAnnotation<PersistenceCreator>(),
            "Class[$entityName] Create method must have @PersistenceCreator annotation",
        )
        assertEquals(
            entityKClass,
            createMethod.returnType.jvmErasure,
            "Class[$entityName] Create method must return an instance of the target class",
        )

        val primaryConstructor = entityKClass.primaryConstructor
        assertNotNull(
            primaryConstructor,
            "Class[$entityName] must have a primary constructor",
        )
        assertPersistenceCreatorSignature(entityName, primaryConstructor, createMethod)
    }

    private fun assertPersistenceCreatorSignature(
        entityName: String?,
        primaryConstructor: KFunction<*>?,
        createMethod: KFunction<*>,
    ) {
        val constructorParameters = primaryConstructor!!.parameters
        val factoryParameters = createMethod.valueParameters

        assertEquals(
            constructorParameters.size,
            factoryParameters.size,
            "Class[$entityName] @PersistenceCreator create parameters must match constructor parameters",
        )
        for (index in constructorParameters.indices) {
            val constructorParameter: KParameter = constructorParameters[index]
            val factoryParameter: KParameter = factoryParameters[index]
            assertEquals(
                constructorParameter.name,
                factoryParameter.name,
                "Class[$entityName] @PersistenceCreator parameter names must match constructor parameters",
            )
            assertTrue(
                factoryParameter.type.isMarkedNullable,
                "Class[$entityName] @PersistenceCreator parameter[${factoryParameter.name}] must be nullable",
            )
            assertEquals(
                constructorParameter.type.withNullability(true),
                factoryParameter.type,
                "Class[$entityName] @PersistenceCreator parameter[${factoryParameter.name}] " +
                        "must be the nullable version of constructor parameter type",
            )
        }
    }
}
