package com.gradle.optimization.mcp.features.declarative.api

import kotlinx.serialization.Serializable

@Serializable
data class DeclarativeSchemaRequest(
    val projectDir: String,
    val subprojectPath: String? = null,
    val maxFiles: Int = DEFAULT_MAX_FILES,
    val maxElementsPerFile: Int = DEFAULT_MAX_ELEMENTS_PER_FILE
) {
    companion object {
        const val DEFAULT_MAX_FILES = 40
        const val DEFAULT_MAX_ELEMENTS_PER_FILE = 30
    }
}

@Serializable
data class SoftwareTypeDefinition(
    val name: String,
    val targetType: String,
    val providerPlugin: String,
    val description: String
)

@Serializable
data class DeclarativeFileInfo(
    val filePath: String,
    val subproject: String,
    val elements: List<String>,
    val softwareTypesUsed: List<String>,
    val elementsTruncated: Boolean = false
)

@Serializable
data class DeclarativeConfigurationOption(
    val blockName: String,
    val propertyName: String,
    val type: String,
    val description: String
)

@Serializable
data class DeclarativeSchemaResult(
    val projectDir: String,
    val hasDeclarativeGradle: Boolean,
    val status: String,
    val detectionMethod: String,
    val scannedRoots: List<String>,
    val registeredSoftwareTypes: List<SoftwareTypeDefinition>,
    val declarativeFiles: List<DeclarativeFileInfo>,
    val declarativeFilesTotal: Int = 0,
    val truncated: Boolean = false,
    val exposedConfigurations: List<DeclarativeConfigurationOption>,
    val warnings: List<String>,
    val recommendations: List<String>,
    val summary: String
) {
    companion object {
        /** No `.dcl` files and no known Declarative ecosystem plugin IDs. */
        const val STATUS_NOT_DECLARATIVE = "NOT_DECLARATIVE"

        /** Ecosystem plugin IDs found, but no `.dcl` files in scope. */
        const val STATUS_NO_DCL = "NO_DCL"

        /** One or more `.dcl` files found in scope. */
        const val STATUS_HAS_DCL = "HAS_DCL"

        const val DETECTION_METHOD =
            "static .dcl file walk + exact ecosystem plugin ID match in settings/build scripts"
    }
}
