package com.mikai233.tools.excel

import com.alibaba.excel.EasyExcel
import com.mikai233.common.annotation.NoArg
import com.mikai233.common.extension.snakeCaseToUpperCamelCase
import com.mikai233.shared.excel.GameConfigManager.Companion.HEADER_SIZE
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.io.File
import kotlin.io.path.Path


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
    val fieldsDocs = fields.map { (name, _, _, comment) ->
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
    val superClassMap = Map::class.asClassName().parameterizedBy(id.type, gameConfigClass)
    return TypeSpec.classBuilder("${configName}Configs")
        .addAnnotation(NoArg::class)
        .superclass(superClassGameConfigs)
        .addSuperinterface(superClassMap)
        .addFunctions(buildGameConfigsFunctions(excelName, gameConfigClass, id, fields))
        .build()
}

fun buildGameConfigsFunctions(
    excelName: String,
    configClass: ClassName,
    id: ExcelField,
    fields: List<ExcelField>
): List<FunSpec> {
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

/**
 * 生成配置文件
 */
//fun main(args: Array<String>) {
//    if (args.size != 2) {
//        println("程序参数数量错误${args.size}，请保证arg1为配置excel文件路径，arg2为配置excel文件名称（多个文件以;隔开）")
//        exitProcess(-1)
//    }
//    val excelDir = args[0].replace("\\", "/")
//    val excelNames = args[1].split(";")
//    val targetDir = "shared/src/main/kotlin"
//    excelNames.forEach { excelName ->
//        EasyExcel.read(
//            "$excelDir/$excelName",
//            ExcelHeaderListener { id, fields ->
//                if (id != null) {
//                    println("Generating $excelName")
//                    val file = buildConfigFile(excelName.removeSuffix(".xlsx"), id, fields)
//                    file.writeTo(File(targetDir))
//                    println("Generated ${file.packageName}.${file.name} to $targetDir/${file.relativePath}")
//                } else {
//                    println("Skip $excelName because of no id")
//                }
//            }
//        ).headRowNumber(HEADER_SIZE).sheet().doRead()
//    }
//}

/**
 * 生成配置文件
 */
fun main() {
    //遍历目录下的所有xlsx文件生成代码
    val excelDir = "F:\\MiscProjects\\design\\OutPut\\cn\\ExportSheet".replace("\\", "/")
    val targetDir = "shared/src/main/kotlin"
    val excelNames = File(excelDir).listFiles()!!.map { it.name }
    excelNames.forEach { excelName ->
        println("Processing $excelName")
        EasyExcel.read(
            "$excelDir/$excelName",
            ExcelHeaderListener { id, fields ->
                if (id != null) {
                    println("Generating $excelName")
                    val file = buildConfigFile(excelName.removeSuffix(".xlsx"), id, fields)
                    file.writeTo(Path(targetDir))
                    println("Generated ${file.packageName}.${file.name} to $targetDir/${file.relativePath}")
                } else {
                    println("Skip $excelName because of no id")
                }
            }
        ).headRowNumber(HEADER_SIZE).sheet().doRead()
    }
}