package com.mikai233.messageksp

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.mikai233.common.annotation.GameConfigChangeHandler
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

private const val OPTION_PACKAGE = "antares.config.package"
private const val OPTION_CLASS = "antares.config.class"
private const val OPTION_ACTOR_TYPE = "antares.config.actorType"

class ConfigChangeHandlerProcessor(
    environment: SymbolProcessorEnvironment,
) : SymbolProcessor {
    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private val logger: KSPLogger = environment.logger
    private val packageName: String = environment.options[OPTION_PACKAGE]
        ?: error("missing KSP option $OPTION_PACKAGE")
    private val className: String = environment.options[OPTION_CLASS]
        ?: error("missing KSP option $OPTION_CLASS")
    private val actorTypeName: String = environment.options[OPTION_ACTOR_TYPE]
        ?: error("missing KSP option $OPTION_ACTOR_TYPE")
    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()

        val annotationName = GameConfigChangeHandler::class.qualifiedName
            ?: error("missing annotation qualified name")
        val symbols = resolver.getSymbolsWithAnnotation(annotationName)
        val (valid, invalid) = symbols.partition { it is KSClassDeclaration && it.validate() }
        val handlers = valid.map { it as KSClassDeclaration }.sortedBy { it.qualifiedName?.asString() }

        validateHandlers(resolver, handlers)
        generateFile(handlers)
        generated = true
        return invalid
    }

    private fun validateHandlers(resolver: Resolver, handlers: List<KSClassDeclaration>) {
        val targetInterface = resolver.getClassDeclarationByName(
            resolver.getKSNameFromString("com.mikai233.common.config.ConfigChangeHandler"),
        ) ?: error("cannot resolve ConfigChangeHandler")
        val actorType = resolver.getClassDeclarationByName(resolver.getKSNameFromString(actorTypeName))
            ?: error("cannot resolve actor type $actorTypeName")

        handlers.forEach { declaration ->
            check(declaration.classKind == ClassKind.CLASS) {
                "@GameConfigChangeHandler only supports classes: ${declaration.qualifiedName?.asString()}"
            }
            check(Modifier.ABSTRACT !in declaration.modifiers) {
                "config change handler must be concrete: ${declaration.qualifiedName?.asString()}"
            }
            check(declaration.getVisibility().name == "PUBLIC") {
                "config change handler must be public: ${declaration.qualifiedName?.asString()}"
            }
            val zeroArgCtor = declaration.primaryConstructor?.parameters?.isEmpty() ?: true
            check(zeroArgCtor) {
                "config change handler must have a zero-arg primary constructor: ${declaration.qualifiedName?.asString()}"
            }
            val implemented = declaration.superTypes
                .map { it.resolve() }
                .firstOrNull { it.declaration.qualifiedName?.asString() == targetInterface.qualifiedName?.asString() }
            checkNotNull(implemented) {
                "@GameConfigChangeHandler class must implement ConfigChangeHandler: ${declaration.qualifiedName?.asString()}"
            }
            val typeArg = implemented.arguments.singleOrNull()?.type?.resolve()
            check(typeArg?.declaration?.qualifiedName?.asString() == actorType.qualifiedName?.asString()) {
                "config change handler ${declaration.qualifiedName?.asString()} must implement ConfigChangeHandler<$actorTypeName>"
            }
        }
    }

    private fun generateFile(handlers: List<KSClassDeclaration>) {
        val actorType = ClassName.bestGuess(actorTypeName)
        val handlerType = ClassName("com.mikai233.common.config", "ConfigChangeHandler").parameterizedBy(actorType)
        val listType = ClassName("kotlin.collections", "List").parameterizedBy(handlerType)
        val objectBuilder = TypeSpec.objectBuilder(className)
        val property = PropertySpec.builder("ALL", listType, KModifier.PUBLIC)
            .initializer(buildInitializer(handlers))
            .build()
        objectBuilder.addProperty(property)

        val file = FileSpec.builder(packageName, className)
            .addType(objectBuilder.build())
            .build()

        file.writeTo(
            codeGenerator = codeGenerator,
            dependencies = Dependencies(
                aggregating = true,
                sources = handlers.mapNotNull { it.containingFile }.toTypedArray(),
            ),
        )
    }

    private fun buildInitializer(handlers: List<KSClassDeclaration>): CodeBlock {
        if (handlers.isEmpty()) {
            return CodeBlock.of("emptyList()")
        }
        val builder = CodeBlock.builder()
        builder.add("listOf(\n")
        handlers.forEachIndexed { index, handler ->
            builder.add("    %T()", handler.toClassName())
            if (index != handlers.lastIndex) builder.add(",")
            builder.add("\n")
        }
        builder.add(")")
        return builder.build()
    }
}
