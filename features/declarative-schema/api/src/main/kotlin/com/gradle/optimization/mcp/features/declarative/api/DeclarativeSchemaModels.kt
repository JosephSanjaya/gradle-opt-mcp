package com.gradle.optimization.mcp.features.declarative.api

import kotlinx.serialization.Serializable

@Serializable
data class DeclarativeSchemaRequest(
    val projectDir: String,
    val subprojectPath: String? = null
)

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
    val softwareTypesUsed: List<String>
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
    val registeredSoftwareTypes: List<SoftwareTypeDefinition>,
    val declarativeFiles: List<DeclarativeFileInfo>,
    val exposedConfigurations: List<DeclarativeConfigurationOption>,
    val warnings: List<String>,
    val recommendations: List<String>
)
