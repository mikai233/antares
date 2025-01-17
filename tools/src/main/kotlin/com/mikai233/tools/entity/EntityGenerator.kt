package com.mikai233.tools.entity

import com.mikai233.common.db.Entity
import com.mikai233.common.extension.upperCamelToSnakeCase
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlin.io.path.Path
import kotlin.random.Random

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/4
 * 生成任意数量的Entity用于测试
 */

private val AllTypes = mutableListOf<TypeSpec>()

private var FieldClassCount = 0

fun main() {
    repeat(100) { randomClass(it, 0) }
    AllTypes.forEach { typeSpec ->
        FileSpec.builder("com.mikai233.common.entity.test", typeSpec.name!!)
            .addType(typeSpec)
            .build()
            .writeTo(Path("common/src/main/kotlin"))
    }
}

private enum class FieldType {
    Int,
    Long,
    Double,
    String,
    Boolean,
    Float,
    Collection,
    Map,
    Class,
    EnumClass,
}

private enum class CollectionType {
    List,
    MutableList,
    Set,
    MutableSet,
    Array,
    ArrayList,
    HashSet,
    LinkedHashSet,
}

private enum class MapType {
    Map,
    MutableMap,
    HashMap,
    LinkedHashMap,
}

private enum class MapKeyType {
    Int,
    Long,
    String,
}

private fun randomFieldType(index: Int?, depth: Int): TypeName {
    val randomType = if (depth > 3) {
        FieldType.entries.filter { it != FieldType.Class }.random()
    } else {
        FieldType.entries.random()
    }
    return when (randomType) {
        FieldType.Int -> Int::class.asTypeName()
        FieldType.Long -> Long::class.asTypeName()
        FieldType.Double -> Double::class.asTypeName()
        FieldType.String -> String::class.asTypeName()
        FieldType.Boolean -> Boolean::class.asTypeName()
        FieldType.Float -> Float::class.asTypeName()
        FieldType.Collection -> randomCollectionFieldType(index, depth)
        FieldType.Map -> randomMapFieldType(index, depth)
        FieldType.Class -> randomClass(index, depth)
        FieldType.EnumClass -> randomEnumClass()
    }
}

private fun randomCollectionFieldType(index: Int?, depth: Int): TypeName {
    val randomType = CollectionType.entries.random()
    return when (randomType) {
        CollectionType.List -> {
            List::class.asClassName().parameterizedBy(randomFieldType(index, depth))
        }

        CollectionType.MutableList -> {
            MutableList::class.asClassName().parameterizedBy(randomFieldType(index, depth))
        }

        CollectionType.Set -> {
            Set::class.asClassName().parameterizedBy(randomFieldType(index, depth))
        }

        CollectionType.MutableSet -> {
            MutableSet::class.asClassName().parameterizedBy(randomFieldType(index, depth))
        }

        CollectionType.Array -> {
            Array::class.asClassName().parameterizedBy(randomFieldType(index, depth))
        }

        CollectionType.ArrayList -> {
            ArrayList::class.asClassName().parameterizedBy(randomFieldType(index, depth))
        }

        CollectionType.HashSet -> {
            HashSet::class.asClassName().parameterizedBy(randomFieldType(index, depth))
        }

        CollectionType.LinkedHashSet -> {
            LinkedHashSet::class.asClassName().parameterizedBy(randomFieldType(index, depth))
        }
    }
}

private fun randomMapFieldType(index: Int?, depth: Int): TypeName {
    val randomType = MapType.entries.random()
    return when (randomType) {
        MapType.Map -> {
            Map::class.asClassName().parameterizedBy(randomMapKeyType(), randomFieldType(index, depth))
        }

        MapType.MutableMap -> {
            MutableMap::class.asClassName().parameterizedBy(randomMapKeyType(), randomFieldType(index, depth))
        }

        MapType.HashMap -> {
            HashMap::class.asClassName().parameterizedBy(randomMapKeyType(), randomFieldType(index, depth))
        }

        MapType.LinkedHashMap -> {
            LinkedHashMap::class.asClassName().parameterizedBy(randomMapKeyType(), randomFieldType(index, depth))
        }
    }
}

private fun randomMapKeyType(): ClassName {
    val randomKeyType = MapKeyType.entries.random()
    return when (randomKeyType) {
        MapKeyType.Int -> Int::class.asClassName()
        MapKeyType.Long -> Long::class.asClassName()
        MapKeyType.String -> String::class.asClassName()
    }
}

private fun randomClass(index: Int?, depth: Int): ClassName {
    val className = if (index == null) {
        ClassName("com.mikai233.common.entity.test", "FieldClass${FieldClassCount++}")
    } else {
        ClassName("com.mikai233.common.entity.test", "TestEntity$index")
    }
    buildType(className, index != null, depth)
    return className
}

private fun randomEnumClass(): ClassName {
    val enumName = "FieldEnum${FieldClassCount++}"
    val typeSpecBuilder = TypeSpec.enumBuilder(enumName)
    repeat(Random.nextInt(1, 5)) {
        typeSpecBuilder.addEnumConstant("Field$it")
    }
    AllTypes.add(typeSpecBuilder.build())
    return ClassName("com.mikai233.common.entity.test", enumName)
}

private fun buildType(className: ClassName, isEntity: Boolean, depth: Int) {
    val typeSpecBuilder = TypeSpec.classBuilder(className)
        .addModifiers(KModifier.DATA)
    val properties = mutableListOf<PropertySpec>()
    val fieldNum = Random.nextInt(10, 30)
    repeat(fieldNum) {
        val fieldName = "field$it"
        val fieldType = randomFieldType(null, depth + 1)
        val propertySpec = PropertySpec.builder(fieldName, fieldType)
            .mutable()
            .initializer(fieldName)
            .build()
        properties.add(propertySpec)
    }
    if (isEntity) {
        typeSpecBuilder.addSuperinterface(Entity::class)
        // 导入的注解类
        val documentAnnotation = ClassName("org.springframework.data.mongodb.core.mapping", "Document")
        val idAnnotation = ClassName("org.springframework.data.annotation", "Id")
        val persistenceCreatorAnnotation = ClassName("org.springframework.data.annotation", "PersistenceCreator")
        val jvmStaticAnnotation = ClassName("kotlin.jvm", "JvmStatic")
        // @Document(collection = "xxx")
        val documentAnnotationSpec = AnnotationSpec.Companion.builder(documentAnnotation)
            .addMember("collection = %S", className.simpleName.upperCamelToSnakeCase())
            .build()
        val idPropertySpec = PropertySpec.builder("id", Int::class)
            .addAnnotation(idAnnotation)
            .initializer("id")
            .build()
        properties.add(0, idPropertySpec)

        // 伴生对象中的 create 方法
        val createFunction = FunSpec.builder("create")
            .addAnnotation(persistenceCreatorAnnotation)
            .addModifiers(KModifier.PUBLIC)
            .returns(className)
            .addCode("throw UnsupportedOperationException()")
            .build()

        // companion object
        val companionObject = TypeSpec.companionObjectBuilder()
            .addModifiers(KModifier.COMPANION)
            .addFunction(createFunction.toBuilder().addAnnotation(jvmStaticAnnotation).build())
            .build()
        typeSpecBuilder.addType(companionObject)
        typeSpecBuilder.addAnnotation(documentAnnotationSpec)
    }
    // 类生成
    typeSpecBuilder
        .addProperties(properties)
        .primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameters(
                    properties.map { propertySpec ->
                        ParameterSpec.builder(propertySpec.name, propertySpec.type).build()
                    }
                )
                .build()
        )
    AllTypes.add(typeSpecBuilder.build())
}