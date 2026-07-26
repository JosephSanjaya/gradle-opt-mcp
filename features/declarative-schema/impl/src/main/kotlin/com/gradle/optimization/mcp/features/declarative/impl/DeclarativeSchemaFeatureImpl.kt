package com.gradle.optimization.mcp.features.declarative.impl

import com.gradle.optimization.mcp.features.declarative.api.DeclarativeConfigurationOption
import com.gradle.optimization.mcp.features.declarative.api.DeclarativeFileInfo
import com.gradle.optimization.mcp.features.declarative.api.DeclarativeSchemaFeatureApi
import com.gradle.optimization.mcp.features.declarative.api.DeclarativeSchemaRequest
import com.gradle.optimization.mcp.features.declarative.api.DeclarativeSchemaResult
import com.gradle.optimization.mcp.features.declarative.api.SoftwareTypeDefinition
import java.io.File
import org.koin.core.annotation.Single

@Single
class DeclarativeSchemaFeatureImpl : DeclarativeSchemaFeatureApi {
    override fun inspectDeclarativeSchemas(request: DeclarativeSchemaRequest): DeclarativeSchemaResult {
        val rootDir = File(request.projectDir)
        require(rootDir.exists() && rootDir.isDirectory) {
            "Project directory does not exist or is not a directory: ${request.projectDir}"
        }

        val subprojectPath = request.subprojectPath?.takeIf { it.isNotBlank() }
        val scanDir = resolveScanDir(rootDir, subprojectPath)
        val scannedRoots = listOf(
            if (subprojectPath == null) {
                "."
            } else {
                gradlePathToRelativeDir(normalizeGradlePath(subprojectPath))
            }
        )

        val maxFiles = request.maxFiles.coerceAtLeast(0)
        val maxElements = request.maxElementsPerFile.coerceAtLeast(0)

        val allDclFiles = findDeclarativeFiles(rootDir, scanDir, maxElements)
        val truncated = allDclFiles.size > maxFiles
        val dclFiles = if (maxFiles == 0) emptyList() else allDclFiles.take(maxFiles)

        val pluginIds = findEcosystemPluginIds(rootDir)
        val hasDcl = allDclFiles.isNotEmpty()
        val hasPlugins = pluginIds.isNotEmpty()
        val hasDeclarative = hasDcl || hasPlugins

        val status = when {
            hasDcl -> DeclarativeSchemaResult.STATUS_HAS_DCL
            hasPlugins -> DeclarativeSchemaResult.STATUS_NO_DCL
            else -> DeclarativeSchemaResult.STATUS_NOT_DECLARATIVE
        }

        val registeredTypes = detectSoftwareTypes(dclFiles)
        val exposedConfigs = STANDARD_DECLARATIVE_OPTIONS.filter { option ->
            registeredTypes.any { it.name == option.blockName }
        }

        val hasKtsOrGradle = hasImperativeBuildScripts(scanDir)
        val warnings = generateWarnings(dclFiles, hasPlugins, hasDcl, hasKtsOrGradle)
        val recommendations = generateRecommendations(hasDeclarative, hasDcl, hasKtsOrGradle)

        val summary = buildSummary(
            projectDir = rootDir.absolutePath,
            status = status,
            hasDeclarative = hasDeclarative,
            dclCount = allDclFiles.size,
            shownCount = dclFiles.size,
            truncated = truncated,
            scannedRoots = scannedRoots,
            pluginIds = pluginIds,
            registeredTypes = registeredTypes,
            recommendations = recommendations
        )

        return DeclarativeSchemaResult(
            projectDir = rootDir.absolutePath,
            hasDeclarativeGradle = hasDeclarative,
            status = status,
            detectionMethod = DeclarativeSchemaResult.DETECTION_METHOD,
            scannedRoots = scannedRoots,
            registeredSoftwareTypes = registeredTypes,
            declarativeFiles = dclFiles,
            declarativeFilesTotal = allDclFiles.size,
            truncated = truncated,
            exposedConfigurations = exposedConfigs,
            warnings = warnings,
            recommendations = recommendations,
            summary = summary
        )
    }

    private fun resolveScanDir(rootDir: File, subprojectPath: String?): File {
        if (subprojectPath == null) return rootDir
        val normalized = normalizeGradlePath(subprojectPath)
        if (normalized == ":") return rootDir
        val relative = gradlePathToRelativeDir(normalized)
        val dir = File(rootDir, relative)
        require(dir.exists() && dir.isDirectory) {
            "Unknown subprojectPath: '$subprojectPath' (expected directory '$relative' under ${rootDir.path})"
        }
        return dir
    }

    private fun findDeclarativeFiles(
        rootDir: File,
        scanDir: File,
        maxElements: Int
    ): List<DeclarativeFileInfo> {
        val fileInfos = mutableListOf<DeclarativeFileInfo>()
        scanDir.walkTopDown()
            .onEnter { dir -> dir.name !in EXCLUDED_DIRS }
            .forEach { file ->
                if (!file.isFile || !file.name.endsWith(".dcl")) return@forEach
                val relativePath = file.relativeTo(rootDir).path.replace(File.separatorChar, '/')
                val content = file.readText()
                val allElements = parseDclElements(content)
                val elementsTruncated = maxElements > 0 && allElements.size > maxElements
                val elements = if (maxElements == 0) emptyList() else allElements.take(maxElements)
                val softwareTypes = KNOWN_SOFTWARE_TYPES
                    .filter { type -> content.contains(type.name) }
                    .map { it.name }
                fileInfos.add(
                    DeclarativeFileInfo(
                        filePath = relativePath,
                        subproject = gradlePathForFile(rootDir, file),
                        elements = elements,
                        softwareTypesUsed = softwareTypes,
                        elementsTruncated = elementsTruncated
                    )
                )
            }
        return fileInfos.sortedBy { it.filePath }
    }

    private fun parseDclElements(content: String): List<String> {
        val regex = Regex("""^\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*\{""", RegexOption.MULTILINE)
        return regex.findAll(content).map { it.groupValues[1] }.distinct().toList()
    }

    private fun findEcosystemPluginIds(rootDir: File): List<String> {
        val scriptFiles = mutableListOf<File>()
        listOf(
            "settings.gradle.kts",
            "settings.gradle",
            "build.gradle.kts",
            "build.gradle"
        ).forEach { name ->
            File(rootDir, name).takeIf { it.isFile }?.let { scriptFiles.add(it) }
        }
        rootDir.walkTopDown()
            .onEnter { dir -> dir.name !in EXCLUDED_DIRS }
            .forEach { file ->
                if (file.isFile &&
                    file.parentFile != rootDir &&
                    file.name in PLUGIN_SCAN_FILENAMES
                ) {
                    scriptFiles.add(file)
                }
            }

        val found = linkedSetOf<String>()
        for (file in scriptFiles) {
            val text = file.readText()
            for (pluginId in ECOSYSTEM_PLUGIN_IDS) {
                if (text.contains(pluginId)) {
                    found.add(pluginId)
                }
            }
        }
        return found.toList()
    }

    private fun detectSoftwareTypes(dclFiles: List<DeclarativeFileInfo>): List<SoftwareTypeDefinition> {
        val usedTypeNames = dclFiles.flatMap { it.softwareTypesUsed }.toSet()
        if (usedTypeNames.isEmpty()) return emptyList()
        return KNOWN_SOFTWARE_TYPES.filter { it.name in usedTypeNames }
    }

    private fun hasImperativeBuildScripts(scanDir: File): Boolean =
        scanDir.walkTopDown()
            .onEnter { dir -> dir.name !in EXCLUDED_DIRS }
            .any { file ->
                file.isFile && (
                    file.name.endsWith(".gradle.kts") ||
                        (file.name.endsWith(".gradle") && !file.name.endsWith(".gradle.dcl"))
                    )
            }

    private fun generateWarnings(
        dclFiles: List<DeclarativeFileInfo>,
        hasPlugins: Boolean,
        hasDcl: Boolean,
        hasKtsOrGradle: Boolean
    ): List<String> {
        if (!hasDcl && !hasPlugins) return emptyList()
        val list = mutableListOf<String>()
        if (hasDcl && hasKtsOrGradle) {
            list.add(
                "Mixed imperative Gradle scripts (.gradle / .gradle.kts) and Declarative Gradle (.dcl) " +
                    "files detected in scan scope."
            )
        }
        if (!hasDcl && hasPlugins) {
            list.add(
                "Declarative Gradle ecosystem plugin ID(s) found in settings/build scripts " +
                    "but no .dcl files in scan scope."
            )
        }
        if (hasDcl && dclFiles.all { it.softwareTypesUsed.isEmpty() }) {
            list.add(
                "Found .dcl files but no known software-type blocks " +
                    "(${KNOWN_SOFTWARE_TYPES.joinToString { it.name }})."
            )
        }
        return list
    }

    private fun generateRecommendations(
        hasDeclarative: Boolean,
        hasDcl: Boolean,
        hasKtsOrGradle: Boolean
    ): List<String> {
        if (!hasDeclarative) {
            return listOf(
                "This project does not appear to use Declarative Gradle " +
                    "(no .dcl files and no known ecosystem plugin IDs). " +
                    "Optional: adopt .dcl software types if you want Declarative Gradle."
            )
        }
        val list = mutableListOf<String>()
        if (hasDcl && hasKtsOrGradle) {
            list.add(
                "Migrate remaining imperative build logic from .gradle/.gradle.kts " +
                    "to declarative software types where feasible."
            )
        }
        if (hasDcl) {
            list.add("Validate .dcl structure against the software types used in these files.")
        }
        return list
    }

    private fun buildSummary(
        projectDir: String,
        status: String,
        hasDeclarative: Boolean,
        dclCount: Int,
        shownCount: Int,
        truncated: Boolean,
        scannedRoots: List<String>,
        pluginIds: List<String>,
        registeredTypes: List<SoftwareTypeDefinition>,
        recommendations: List<String>
    ): String = buildString {
        appendLine("Declarative Gradle static schema scan for $projectDir")
        appendLine("- Scope: ${DeclarativeSchemaResult.DETECTION_METHOD}")
        appendLine("- Status: $status")
        appendLine("- Declarative Gradle Detected: $hasDeclarative")
        appendLine("- Scanned roots: ${scannedRoots.joinToString("; ")}")
        appendLine("- .dcl files: $dclCount" + if (truncated) " (showing $shownCount, truncated)" else "")
        if (pluginIds.isNotEmpty()) {
            appendLine("- Ecosystem plugin IDs: ${pluginIds.joinToString(", ")}")
        }
        if (!hasDeclarative) {
            appendLine("- Note: not using Declarative Gradle (no .dcl / no known ecosystem plugins).")
        }
        if (registeredTypes.isEmpty()) {
            appendLine("- Software types used: none")
        } else {
            appendLine("- Software types used:")
            registeredTypes.forEach { type ->
                appendLine("  - ${type.name} (${type.providerPlugin})")
            }
        }
        recommendations.forEach { appendLine("- Tip: $it") }
    }.trimEnd()

    companion object {
        private val EXCLUDED_DIRS = setOf("build", ".gradle", ".git", ".idea", ".agents")

        private val PLUGIN_SCAN_FILENAMES = setOf(
            "settings.gradle.kts",
            "settings.gradle",
            "build.gradle.kts",
            "build.gradle"
        )

        /** Exact ecosystem plugin IDs — never match the substring "declarative". */
        private val ECOSYSTEM_PLUGIN_IDS = listOf(
            "org.gradle.experimental.java-ecosystem",
            "org.gradle.experimental.kotlin-jvm-ecosystem",
            "org.gradle.experimental.kotlin-ecosystem",
            "org.gradle.experimental.android-ecosystem",
            "org.gradle.experimental.swift-ecosystem"
        )

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
                name = "kotlinJvmApplication",
                targetType = "org.jetbrains.kotlin.jvm",
                providerPlugin = "org.gradle.experimental.kotlin-jvm-ecosystem",
                description = "Declarative software type for Kotlin JVM Application builds"
            ),
            SoftwareTypeDefinition(
                name = "kotlinJvmLibrary",
                targetType = "org.jetbrains.kotlin.jvm",
                providerPlugin = "org.gradle.experimental.kotlin-jvm-ecosystem",
                description = "Declarative software type for Kotlin JVM Library builds"
            ),
            SoftwareTypeDefinition(
                name = "kotlinLibrary",
                targetType = "org.jetbrains.kotlin.jvm",
                providerPlugin = "org.gradle.experimental.kotlin-ecosystem",
                description = "Declarative software type for Kotlin JVM Library builds"
            ),
            SoftwareTypeDefinition(
                name = "androidApplication",
                targetType = "com.android.build.api.dsl.ApplicationExtension",
                providerPlugin = "org.gradle.experimental.android-ecosystem",
                description = "Declarative software type for Android Application builds"
            ),
            SoftwareTypeDefinition(
                name = "androidLibrary",
                targetType = "com.android.build.api.dsl.LibraryExtension",
                providerPlugin = "org.gradle.experimental.android-ecosystem",
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
                blockName = "kotlinJvmLibrary",
                propertyName = "javaVersion",
                type = "Int",
                description = "Target Java language/bytecode version for Kotlin JVM library"
            ),
            DeclarativeConfigurationOption(
                blockName = "kotlinLibrary",
                propertyName = "kotlinVersion",
                type = "String",
                description = "Kotlin compiler target language version"
            )
        )

        internal fun normalizeGradlePath(path: String): String {
            val trimmed = path.trim()
            if (trimmed.isEmpty() || trimmed == ":") return ":"
            val withColon = if (trimmed.startsWith(":")) trimmed else ":$trimmed"
            return withColon.trimEnd(':').ifEmpty { ":" }
        }

        internal fun gradlePathToRelativeDir(normalizedPath: String): String {
            if (normalizedPath == ":") return "."
            return normalizedPath.removePrefix(":").replace(':', File.separatorChar)
        }

        internal fun gradlePathForFile(rootDir: File, file: File): String {
            val parent = file.parentFile ?: return ":"
            val relative = parent.relativeTo(rootDir).path
            if (relative.isEmpty() || relative == ".") return ":"
            return ":" + relative.replace(File.separatorChar, ':')
        }
    }
}
