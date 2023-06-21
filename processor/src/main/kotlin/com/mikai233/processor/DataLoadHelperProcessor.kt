package com.mikai233.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.mikai233.common.core.component.ActorDatabase
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

class DataLoadHelperProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
    private val kotlinVersion: KotlinVersion,
) : SymbolProcessor {
    companion object {
        const val AnnotationGenLoader = "com.mikai233.common.annotation.GenerateLoader"
        const val AnnotationLoadMe = "com.mikai233.common.annotation.LoadMe"
    }

    private val actorDatabase = ActorDatabase::class.asClassName()
    private val template = ClassName("org.springframework.data.mongodb.core", "MongoTemplate")
    private val loaders = mutableMapOf<String, Pair<TypeSpec, FunSpec>>()


    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation(AnnotationGenLoader).forEach { dataMgrDeclaration ->
            val annotationGenLoader =
                dataMgrDeclaration.annotations.find { it.annotationType.resolve().declaration.qualifiedName?.asString() == AnnotationGenLoader }!!
            val actorType = annotationGenLoader.arguments.first().value as KSType
            dataMgrDeclaration as KSClassDeclaration
            val memLoadFields = mutableListOf<Pair<String, TypeName>>()
            dataMgrDeclaration.declarations.forEach { memDataPropertyDeclaration ->
                if (memDataPropertyDeclaration is KSPropertyDeclaration) {
                    val loadMe =
                        memDataPropertyDeclaration.annotations.find { it.annotationType.resolve().declaration.qualifiedName?.asString() == AnnotationLoadMe }
                    if (loadMe != null) {
                        val memDataTypeDeclaration =
                            memDataPropertyDeclaration.type.resolve().declaration as KSClassDeclaration
                        val memDataInterface =
                            requireNotNull(memDataTypeDeclaration.superTypes.find { it.resolve().declaration.qualifiedName?.asString() == "com.mikai233.common.db.MemData" }) { "$AnnotationLoadMe property type must impl com.mikai233.common.db.MemData" }
                        val returnType = memDataInterface.resolve().arguments.last()
                        memLoadFields.add(memDataPropertyDeclaration.simpleName.asString() to returnType.toTypeName())
                    }
                }
            }
            val mgrName = dataMgrDeclaration.simpleName.asString()
            val loadedDataSpec = genLoadedData(mgrName, memLoadFields)
            val funSpec = genLoadAll(
                dataMgrDeclaration.toClassName(),
                actorType.toTypeName(),
                "${mgrName}LoadedData",
                memLoadFields
            )
            loaders["${mgrName}Loader"] = loadedDataSpec to funSpec
        }
        return emptyList()
    }

    private fun genLoadedData(mgrName: String, fields: List<Pair<String, TypeName>>): TypeSpec {
        return TypeSpec.classBuilder("${mgrName}LoadedData")
            .addModifiers(KModifier.DATA, KModifier.PRIVATE)
            .primaryConstructor(
                FunSpec.constructorBuilder().apply {
                    fields.forEach { (name, type) ->
                        addParameter("${name}Data", type)
                    }
                }
                    .build()
            ).apply {
                fields.forEach { (name, type) ->
                    addProperty(
                        PropertySpec.builder("${name}Data", type)
                            .initializer("${name}Data")
                            .build()
                    )
                }
            }
            .build()
    }

    private fun genLoadAll(
        receiverType: ClassName,
        actorType: TypeName,
        loadedDataName: String,
        fields: List<Pair<String, TypeName>>
    ): FunSpec {
        val withContextMember = MemberName("kotlinx.coroutines", "withContext")
        val dispatchersMember = MemberName("kotlinx.coroutines", "Dispatchers")
        return FunSpec.builder("loadAllExt")
            .addModifiers(KModifier.SUSPEND)
            .receiver(receiverType)
            .addParameter("actor", actorType)
            .addParameter("db", actorDatabase)
            .addParameter("template", template)
            .addCode(buildCodeBlock {
                beginControlFlow("val loadedData = %M(%M.IO)", withContextMember, dispatchersMember)
                fields.forEachIndexed { index, (name, _) ->
                    addStatement("val loaded%L = %L.load(actor, template)", index, name)
                }
                addStatement("%L(%L)", loadedDataName, fields.indices.joinToString { "loaded$it" })
                endControlFlow()
                fields.forEach { (name, _) ->
                    addStatement("%L.onComplete(actor, db, loadedData.%L)", name, "${name}Data")
                }
                addStatement("loadComplete()")
            })
            .build()
    }

    override fun finish() {
        loaders.forEach { (name, loader) ->
            val fileSpec = FileSpec.builder("com.mikai233.shared", "DataLoader")
                .addType(loader.first)
                .addFunction(loader.second)
                .build()
            codeGenerator.createNewFile(Dependencies(false), "com.mikai233.shared", name).use { os ->
                os.writer().use {
                    fileSpec.writeTo(it)
                }
            }
        }
    }
}
