package com.mikai233.tools.excel

import com.google.common.base.CaseFormat
import com.google.common.collect.ImmutableMap
import com.mikai233.common.excel.*
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.snakeCaseToCamelCase
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.String

private const val extPackage = "com.mikai233.common.excel"
private const val immutablePackage = "com.google.common.collect"

object ExcelGenerator {
    private val logger = logger()
    private val vecInt = ClassName(immutablePackage, "ImmutableList").parameterizedBy(typeNameOf<Int>())
    private val vec2Int = ClassName(immutablePackage, "ImmutableList").parameterizedBy(typeNameOf<IntPair>())
    private val vec3Int = ClassName(immutablePackage, "ImmutableList").parameterizedBy(typeNameOf<IntTriple>())
    private val immutableMap = ClassName("com.google.common.collect", "ImmutableMap")

    @JvmStatic
    fun main(args: Array<String>) {
        check(args.isNotEmpty()) { "please input excel path" }
        val path = args[0]
        logger.info("{}", path)
        val file = File(path)
        val excelNameWithExt = file.name
        val excelName = file.nameWithoutExtension
        val excelClassName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, excelName)
        val context = EasyExcelContext(path)
        val columnName = context.header[0]
        val excelTypes = context.header[1].map { Type.form(it) }
        val excelKeys = context.header[2].map { ExcelKey.form(it) }
        check(columnName.size == excelTypes.size && excelTypes.size == excelKeys.size)
        val excelMeta = columnName.zip(excelTypes).zip(excelKeys) { (name, type), key ->
            Triple(name.snakeCaseToCamelCase(), type, key)
        }.filter { it.third == ExcelKey.All || it.third == ExcelKey.Server || it.third == ExcelKey.PrimaryKey }
        check(excelMeta.count { it.third == ExcelKey.PrimaryKey } == 1) { "multi primary key or no primary key" }
        val pk = requireNotNull(excelMeta.find { it.third == ExcelKey.PrimaryKey }) { "primary key not found" }

        val meta = ConfigMeta(
            excelNameWithExt,
            pk.first,
            typeNameFormType(pk.second),
            excelClassName,
            excelMeta.map { it.first to it.second },
        )
        val code = genConfig(meta)
        if (args.getOrNull(1) != null) {
            val outPath = "${args[1]}/$excelClassName.kt"
            val out = File(outPath)
            out.writeText(code)
            logger.info("generate to:{}", outPath)
        } else {
            println(code)
        }
    }

    data class ConfigMeta(
        val excelName: String,
        val pkName: String,
        val pkType: TypeName,
        val rowName: String,
        val fields: List<Pair<String, Type>>,
        val pkg: String = "com.mikai233.shared.excel",
    )

    private fun genConfig(meta: ConfigMeta): String {
        val rowName = "${meta.rowName}Row"
        val configName = "${meta.rowName}Config"
        val types = meta.fields.map { (name, type) -> name to typeNameFormType(type) }
        val row = rowClass(rowName, meta.pkName, meta.pkType, types)
        val type = configClass(
            ClassName(meta.pkg, rowName),
            meta.pkType,
            meta.fields,
            configName,
            meta.excelName,
        )
        val file = FileSpec.builder(ClassName(meta.pkg, configName)).apply {
            addType(row)
            addType(type)
        }
        val generated = StringBuilder()
        file.build().writeTo(generated)
        return generated.toString()
    }

    private fun pk(name: String, pkType: TypeName): FunSpec {
        return FunSpec.builder("primary key")
            .addModifiers(KModifier.OVERRIDE)
            .returns(pkType)
            .addStatement("return %L", name)
            .build()
    }

    private fun rowClass(
        className: String,
        pkName: String,
        pkType: TypeName,
        fields: List<Pair<String, TypeName>>
    ): TypeSpec {
        val excelRowInterface = ClassName(extPackage, "ExcelRow").parameterizedBy(pkType)
        return TypeSpec.classBuilder(className)
            .addModifiers(KModifier.DATA)
            .addAnnotation(Serializable::class)
            .primaryConstructor(
                FunSpec.constructorBuilder().apply {
                    fields.forEach { (name, type) ->
                        addParameter(name, type)
                    }
                }
                    .build()
            ).apply {
                fields.forEach { (name, type) ->
                    addProperty(
                        PropertySpec.builder(name, type)
                            .initializer(name)
                            .build()
                    )
                }
            }
            .addSuperinterface(excelRowInterface)
            .addFunction(pk(pkName, pkType))
            .build()
    }

    private fun configClass(
        rowClass: TypeName,
        pkType: TypeName,
        fields: List<Pair<String, Type>>,
        configName: String,
        excelName: String
    ): TypeSpec {
        val typedRowClass = ClassName(extPackage, "ExcelConfig").parameterizedBy(pkType, rowClass)
        return TypeSpec.classBuilder(configName)
            .superclass(typedRowClass)
            .addFunction(name(excelName))
            .addFunction(load(rowClass, pkType, fields))
            .build()
    }

    private fun load(rowClass: TypeName, pkType: TypeName, fields: List<Pair<String, Type>>): FunSpec {
        return FunSpec.builder("load")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("context", typeNameOf<ExcelContext>())
            .addParameter("manager", typeNameOf<ExcelManager>())
            .addStatement("val rowsBuilder = %T.builder<%T, %T>()", ImmutableMap::class, pkType, rowClass)
            .addCode(buildCodeBlock {
                addStatement("context.forEachRow { row -> ")
                fields.forEach { field ->
                    add(buildLoadCode(rowClass, field.first, field.second))
                }
                addStatement(
                    "val rowData = %T(%L)",
                    rowClass,
                    fields.joinToString(",\n") { "${it.first} = ${it.first}" })
                addStatement(" rowsBuilder.put(rowData.`primary key`(), rowData)")
                addStatement("}")
                addStatement("rows = rowsBuilder.build()")
            }).build()
    }

    private fun buildLoadCode(rowClass: TypeName, field: String, type: Type): CodeBlock {
        val convert = MemberName("com.mikai233.common.ext", "camelCaseToSnakeCase", true)

        fun readExt(name: String): CodeBlock {
            return buildCodeBlock {
                if (type == Type.String || type == Type.Lang) {
                    addStatement("val %L = row.read(%T::%L.name.%M())", field, rowClass, field, convert)
                } else {
                    val read = MemberName(extPackage, name, true)
                    addStatement("val %L = row.%M(%T::%L.name.%M())", field, read, rowClass, field, convert)
                }
            }
        }

        return when (type) {
            Type.Int -> {
                readExt(ExcelReader::readInt.name)
            }

            Type.Long -> {
                readExt(ExcelReader::readLong.name)
            }

            Type.Double -> {
                readExt(ExcelReader::readDouble.name)
            }

            Type.Boolean -> {
                readExt(ExcelReader::readBoolean.name)
            }

            Type.String, Type.Lang -> {
                readExt(ExcelReader::read.name)
            }

            Type.VecInt -> {
                readExt(ExcelReader::readVecInt.name)
            }

            Type.Vec2Int -> {
                readExt(ExcelReader::readVec2Int.name)
            }

            Type.Vec3Int -> {
                readExt(ExcelReader::readVec3Int.name)
            }

            Type.IntPair -> {
                readExt(ExcelReader::readIntPair.name)
            }

            Type.IntTriple -> {
                readExt(ExcelReader::readIntTriple.name)
            }
        }
    }

    private fun name(excelName: String): FunSpec {
        return FunSpec.builder("name")
            .addModifiers(KModifier.OVERRIDE)
            .returns(String::class)
            .addStatement("return %S", excelName)
            .build()
    }

    private fun typeNameFormType(type: Type): TypeName {
        return when (type) {
            Type.Int -> typeNameOf<Int>()
            Type.Long -> typeNameOf<Long>()
            Type.Double -> typeNameOf<Double>()
            Type.Boolean -> typeNameOf<Boolean>()
            Type.String, Type.Lang -> typeNameOf<String>()
            Type.VecInt -> vecInt
            Type.Vec2Int -> vec2Int
            Type.Vec3Int -> vec3Int
            Type.IntPair -> typeNameOf<IntPair>()
            Type.IntTriple -> typeNameOf<IntTriple>()
        }
    }
}