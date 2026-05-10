package com.mikai233.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import javax.inject.Inject

abstract class AntaresScriptsExtension @Inject constructor(objects: ObjectFactory) {
    val sourceSetName: Property<String> = objects.property(String::class.java).convention("script")
    val taskGroup: Property<String> = objects.property(String::class.java).convention("script")
    val manifestAttributeName: Property<String> = objects.property(String::class.java).convention("Script-Class")
    val archiveNamePrefix: Property<String> = objects.property(String::class.java)
}

class AntaresScriptConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<AntaresScriptsExtension>("antaresScripts")
        extension.archiveNamePrefix.convention(project.provider { "${project.rootProject.name}_${project.name}" })

        project.pluginManager.withPlugin("java") {
            project.configureScriptConventions(extension)
        }
    }

    private fun Project.configureScriptConventions(extension: AntaresScriptsExtension) {
        val sourceSets = extensions.getByType<SourceSetContainer>()
        val mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        val scriptSourceSetName = extension.sourceSetName.get()
        val scriptSourceSet = sourceSets.maybeCreate(scriptSourceSetName).apply {
            compileClasspath += mainSourceSet.compileClasspath + mainSourceSet.output
        }

        val scriptJarTasks = ScriptEntryScanner(projectDir, scriptSourceSetName).discover().map { entry ->
            tasks.register<Jar>("scriptJar${entry.simpleName}") {
                group = extension.taskGroup.get()
                description = "Build script JAR for ${entry.qualifiedName}."
                dependsOn(tasks.named(scriptSourceSet.classesTaskName))
                archiveFileName.set(extension.archiveNamePrefix.map { prefix -> "${prefix}_${entry.simpleName}.jar" })
                manifest {
                    attributes(mapOf(extension.manifestAttributeName.get() to entry.qualifiedName))
                }
                from(scriptSourceSet.output)
            }
        }

        tasks.register("scriptJars") {
            group = extension.taskGroup.get()
            description = "Build all script JARs for ${project.path}."
            dependsOn(scriptJarTasks)
        }
    }
}

abstract class AntaresPatchesExtension @Inject constructor(objects: ObjectFactory) {
    val sourceSetName: Property<String> = objects.property(String::class.java).convention("patch")
    val taskGroup: Property<String> = objects.property(String::class.java).convention("patch")
    val manifestAttributeName: Property<String> = objects.property(String::class.java).convention("Patch-Class")
    val archiveNamePrefix: Property<String> = objects.property(String::class.java)
}

class AntaresPatchConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<AntaresPatchesExtension>("antaresPatches")
        extension.archiveNamePrefix.convention(project.provider { "${project.rootProject.name}_${project.name}" })

        project.pluginManager.withPlugin("java") {
            project.configurePatchConventions(extension)
        }
    }

    private fun Project.configurePatchConventions(extension: AntaresPatchesExtension) {
        val sourceSets = extensions.getByType<SourceSetContainer>()
        val mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        val patchSourceSetName = extension.sourceSetName.get()
        val patchSourceSet = sourceSets.maybeCreate(patchSourceSetName).apply {
            compileClasspath += mainSourceSet.compileClasspath + mainSourceSet.output
        }

        val patchJarTasks = ScriptEntryScanner(
            projectDir = projectDir,
            sourceSetName = patchSourceSetName,
            baseTypeRegex = Regex("""\bRuntimePatchPlugin\b"""),
        ).discover().map { entry ->
            tasks.register<Jar>("patchJar${entry.simpleName}") {
                group = extension.taskGroup.get()
                description = "Build runtime patch JAR for ${entry.qualifiedName}."
                dependsOn(tasks.named(patchSourceSet.classesTaskName))
                archiveFileName.set(extension.archiveNamePrefix.map { prefix -> "${prefix}_${entry.simpleName}.jar" })
                manifest {
                    attributes(mapOf(extension.manifestAttributeName.get() to entry.qualifiedName))
                }
                from(patchSourceSet.output)
            }
        }

        tasks.register("patchJars") {
            group = extension.taskGroup.get()
            description = "Build all runtime patch JARs for ${project.path}."
            dependsOn(patchJarTasks)
        }
    }
}
