package com.gradle.optimization.mcp.features.health.api

import kotlinx.serialization.Serializable

@Serializable
data class GradleHealthRequest(
    val projectDir: String
)

@Serializable
data class GradleHealthResult(
    val projectDir: String,
    val gradleVersion: String,
    val javaVersion: String,
    val javaVendor: String,
    val javaHome: String,
    val osName: String,
    val osArch: String,
    val rootProjectName: String,
    val subprojectCount: Int,
    val subprojectNames: List<String>,
    val subprojectsTruncated: Boolean = false,
    val wrapperVersion: String? = null,
    val buildSrcPresent: Boolean = false,
    val configurationCacheEnabled: Boolean = false,
    val cachingEnabled: Boolean = false,
    val parallelEnabled: Boolean = false,
    val gaps: List<String> = emptyList(),
    val summary: String
)
