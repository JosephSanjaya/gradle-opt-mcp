package com.gradle.optimization.mcp.features.declarative.impl

import com.gradle.optimization.mcp.features.declarative.api.DeclarativeConfigurationOption
import com.gradle.optimization.mcp.features.declarative.api.DeclarativeFileInfo
import com.gradle.optimization.mcp.features.declarative.api.DeclarativeSchemaFeatureApi
import com.gradle.optimization.mcp.features.declarative.api.DeclarativeSchemaRequest
import com.gradle.optimization.mcp.features.declarative.api.DeclarativeSchemaResult
import com.gradle.optimization.mcp.features.declarative.api.SoftwareTypeDefinition
import org.koin.core.annotation.Single
import java.io.File

private const val DEFAULT_SOFTWARE_TYPES_LIMIT = 3

private val KNOWN_SOFTWARE_TYPES = listOf(
    SoftwareTypeDefinition(
        name = "javaApplication",
        targetType = "org.gradle.api.plugins.JavaApplication",
        providerPlugin = "org.gradle.experimental.java-ecosystem",
        description = "Declarative software type for Java Application builds"
    ),
    SoftwareTypeDefinition(
        name = "javaLibrary",
        targetType = "org.gradle.api.plugins.JavaLibrary",
        providerPlugin = "org.gradle.experimental.java-ecosystem",
        description = "Declarative software type for Java Library builds"
    ),
    SoftwareTypeDefinition(
        name = "kotlinLibrary",
        targetType = "org.jetbrains.kotlin.jvm",
        providerPlugin = "org.gradle.experimental.kotlin-ecosystem",
        description = "Declarative software type for Kotlin JVM Library builds"
    ),
    SoftwareTypeDefinition(
        name = "androidApp",
        targetType = "com.android.build.api.dsl.ApplicationExtension",
        providerPlugin = "com.android.experimental.declarative",
        description = "Declarative software type for Android Application builds"
    ),
    SoftwareTypeDefinition(
        name = "androidLibrary",
        targetType = "com.android.build.api.dsl.LibraryExtension",
        providerPlugin = "com.android.experimental.declarative",
        description = "Declarative software type for Android Library builds"
    )
)

private val STANDARD_DECLARATIVE_OPTIONS = listOf(
    DeclarativeConfigurationOption(
        blockName = "javaApplication",
        propertyName = "mainClass",
        type = "String",
        description = "Main entrypoint class name for the Java application"
    ),
    DeclarativeConfigurationOption(
        blockName = "javaLibrary",
        propertyName = "javaVersion",
        type = "Int",
        description = "Target Java language/bytecode version compatibility"
    ),
    DeclarativeConfigurationOption(
        blockName = "kotlinLibrary",
        propertyName = "kotlinVersion",
        type = "String",
        description = "Kotlin compiler target language version"
    ),
    DeclarativeConfigurationOption(
        blockName = "dependencies",
        propertyName = "implementation",
        type = "List<Dependency>",
        description = "Implementation dependencies for software component"
    )
)

@Single
class DeclarativeSchemaFeatureImpl : DeclarativeSchemaFeatureApi {
    override fun inspectDeclarativeSchemas(request: DeclarativeSchemaRequest): DeclarativeSchemaResult {
        val targetDir = File(request.projectDir)
        require(targetDir.exists()) { "Project directory does not exist: ${request.projectDir}" }

        val dclFiles = findDeclarativeFiles(targetDir)
        val hasDeclarative = dclFiles.isNotEmpty() || hasDeclarativePlugins(targetDir)

        val registeredTypes = detectSoftwareTypes(dclFiles)
        val exposedConfigs = STANDARD_DECLARATIVE_OPTIONS
        val warnings = generateWarnings(targetDir, dclFiles, hasDeclarative)
        val recommendations = generateRecommendations(hasDeclarative)

        return DeclarativeSchemaResult(
            projectDir = request.projectDir,
            hasDeclarativeGradle = hasDeclarative,
            registeredSoftwareTypes = registeredTypes,
            declarativeFiles = dclFiles,
            exposedConfigurations = exposedConfigs,
            warnings = warnings,
            recommendations = recommendations
        )
    }

    private fun findDeclarativeFiles(targetDir: File): List<DeclarativeFileInfo> {
        val fileInfos = mutableListOf<DeclarativeFileInfo>()
        targetDir.walkTopDown().forEach { file ->
            if (file.isFile && file.name.endsWith(".dcl")) {
                val relativePath = file.relativeTo(targetDir).path
                val content = file.readText()
                val elements = parseDclElements(content)
                val softwareTypes = KNOWN_SOFTWARE_TYPES.filter { content.contains(it.name) }.map { it.name }
                val subproject = file.parentFile.relativeTo(targetDir).path.let {
                    if (it == "" || it == ".") "root" else ":$it"
                }

                fileInfos.add(
                    DeclarativeFileInfo(
                        filePath = relativePath,
                        subproject = subproject,
                        elements = elements,
                        softwareTypesUsed = softwareTypes
                    )
                )
            }
        }
        return fileInfos
    }

    private fun parseDclElements(content: String): List<String> {
        val elements = mutableListOf<String>()
        val regex = Regex("^\\s*([a-zA-Z0-9_]+)\\s*\\{", RegexOption.MULTILINE)
        regex.findAll(content).forEach { match ->
            elements.add(match.groupValues[1])
        }
        return elements.distinct()
    }

    private fun hasDeclarativePlugins(targetDir: File): Boolean {
        val settingsFile = File(targetDir, "settings.gradle.kts").takeIf { it.exists() }
            ?: File(targetDir, "settings.gradle").takeIf { it.exists() }
        val settingsText = settingsFile?.readText() ?: ""
        return settingsText.contains("declarative") || settingsText.contains("softwareType")
    }

    private fun detectSoftwareTypes(dclFiles: List<DeclarativeFileInfo>): List<SoftwareTypeDefinition> {
        val usedTypeNames = dclFiles.flatMap { it.softwareTypesUsed }.toSet()
        return if (usedTypeNames.isEmpty()) {
            KNOWN_SOFTWARE_TYPES.take(DEFAULT_SOFTWARE_TYPES_LIMIT)
        } else {
            KNOWN_SOFTWARE_TYPES.filter { it.name in usedTypeNames }
        }
    }

    private fun generateWarnings(
        targetDir: File,
        dclFiles: List<DeclarativeFileInfo>,
        hasDeclarative: Boolean
    ): List<String> {
        val list = mutableListOf<String>()
        val hasKtsBuild = targetDir.walkTopDown().any { it.name.endsWith(".gradle.kts") }

        if (hasDeclarative && hasKtsBuild) {
            list.add("Mixed imperative Gradle Kotlin DSL (.gradle.kts) and Declarative Gradle (.dcl) files detected.")
        }
        if (dclFiles.isEmpty() && hasDeclarative) {
            list.add("Declarative Gradle ecosystem plugin applied in settings but no .dcl files found.")
        }
        return list
    }

    private fun generateRecommendations(hasDeclarative: Boolean): List<String> {
        val list = mutableListOf<String>()
        if (!hasDeclarative) {
            list.add(
                "Consider adopting Gradle 9.0 Declarative Gradle DSL (.dcl) " +
                    "for declarative software type definitions."
            )
        } else {
            list.add("Migrate remaining imperative build logic from build.gradle.kts to declarative software types.")
            list.add("Use declarative schema validation tools to verify .dcl structure compatibility.")
        }
        return list
    }
}
