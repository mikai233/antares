package com.mikai233.tools.excel

import com.alibaba.excel.EasyExcel
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.mikai233.common.annotation.NoArg
import com.mikai233.common.excel.GameConfigManager.Companion.HEADER_SIZE
import com.mikai233.common.extension.snakeCaseToUpperCamelCase
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.io.File


fun buildGameConfig(configName: String, id: ExcelField, fields: List<ExcelField>): TypeSpec {
    val primaryConstructor = FunSpec.constructorBuilder().let {
        fields.forEach { (name, _, type) ->
            it.addParameter(name, type)
        }
        it.build()
    }
    val properties = fields.map { (name, _, type) ->
        PropertySpec.builder(name, type).initializer(name).build()
    }
    val idFunction = FunSpec.builder("id")
        .addModifiers(KModifier.OVERRIDE)
        .returns(id.type)
        .addStatement("return %L", id.name)
        .build()
    val fieldsDocs = fields.map {
        val name = it.name
        val comment = it.comment
        CodeBlock.of("@param %L %L", name, comment.replace("%", "%%"))
    }
    return TypeSpec.classBuilder("${configName}Config")
        .addModifiers(KModifier.DATA)
        .addAnnotation(NoArg::class)
        .primaryConstructor(primaryConstructor)
        .addProperties(properties)
        .addSuperinterface(GAME_CONFIG.parameterizedBy(id.type))
        .addFunction(idFunction)
        .addKdoc(fieldsDocs.joinToString("\n"))
        .build()
}

fun buildGameConfigs(
    excelName: String,
    configName: String,
    id: ExcelField,
    fields: List<ExcelField>,
): TypeSpec {
    val gameConfigClass = ClassName(GENERATE_PACKAGE, "${configName}Config")
    val superClassGameConfigs = GAME_CONFIGS.parameterizedBy(id.type, gameConfigClass)
    return TypeSpec.classBuilder("${configName}Configs")
        .addAnnotation(NoArg::class)
        .superclass(superClassGameConfigs)
        .addFunctions(buildGameConfigsFunctions(excelName, gameConfigClass, fields))
        .build()
}

fun buildGameConfigsFunctions(excelName: String, configClass: ClassName, fields: List<ExcelField>): List<FunSpec> {
    return listOf(
        FunSpec.builder("excelName")
            .addModifiers(KModifier.OVERRIDE)
            .returns(String::class)
            .addStatement("return %S", "$excelName.xlsx")
            .build(),
        buildParseRowFunction(configClass, fields),
        FunSpec.builder("parseComplete")
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("return Unit")
            .build(),
        FunSpec.builder("validate")
            .addKdoc("TODO: Implement validation logic")
            .addModifiers(KModifier.OVERRIDE)
            .build(),
    )
}

fun buildParseRowFunction(configClass: ClassName, fields: List<ExcelField>): FunSpec {
    val argsPlaceholder = fields.joinToString(", ") { "%N" }
    @Suppress("SpreadOperator")
    return FunSpec.builder("parseRow")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("row", ClassName(INTERFACE_PACKAGE, "Row"))
        .returns(configClass).also {
            fields.forEach { (name, excelName, type) ->
                val parseFn = PARSE_MAPPING[type] ?: error("Unsupported type: $type")
                it.addStatement("val %N = row.%L(%S)", name, parseFn, excelName)
            }
        }
        .addStatement("return %T($argsPlaceholder)", configClass, *fields.map { it.name }.toTypedArray())
        .build()
}

fun buildConfigFile(excelName: String, id: ExcelField, fields: List<ExcelField>): FileSpec {
    val configName = excelName.snakeCaseToUpperCamelCase()
    return FileSpec.builder(GENERATE_PACKAGE, configName)
        .addType(buildGameConfig(configName, id, fields))
        .addType(buildGameConfigs(excelName, configName, id, fields))
        .build()
}

private class GeneratorCli {
    @Parameter(names = ["-d", "--dir"], description = "excel directory", required = true)
    lateinit var dir: String

    @Parameter(names = ["-e", "--excels"], description = "excels, split by ,", required = true)
    lateinit var excels: String
}

/**
 * 生成配置文件
 */
fun main(args: Array<String>) {
    val generatorCli = GeneratorCli()
    @Suppress("SpreadOperator")
    JCommander.newBuilder()
        .addObject(generatorCli)
        .build()
        .parse(*args)
    val excelDir = generatorCli.dir.replace("\\", "/")
    val targetDir = "common/src/main/kotlin"
    generatorCli.excels.split(",").forEach { excelName ->
        EasyExcel.read(
            "$excelDir/$excelName",
            ExcelHeaderListener { id, fields ->
                if (id != null) {
                    println("Generating $excelName")
                    val file = buildConfigFile(excelName.removeSuffix(".xlsx"), id, fields)
                    file.writeTo(File(targetDir))
                    println("Generated ${file.packageName}.${file.name} to $targetDir/${file.relativePath}")
                } else {
                    println("Skip $excelName because of no id")
                }
            }
        ).headRowNumber(HEADER_SIZE).sheet().doRead()
    }
}
