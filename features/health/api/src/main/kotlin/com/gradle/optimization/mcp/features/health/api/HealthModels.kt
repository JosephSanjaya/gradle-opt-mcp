package com.gradle.optimization.mcp.features.health.api

import kotlinx.serialization.Serializable

@Serializable
data class GradleHealthRequest(
    val projectDir: String? = null
)

@Serializable
data class GradleHealthResult(
    val gradleVersion: String,
    val javaVersion: String,
    val javaVendor: String,
    val javaHome: String,
    val osName: String,
    val osArch: String,
    val rootProjectName: String,
    val subprojectCount: Int,
    val subprojectNames: List<String>,
    val wrapperVersion: String? = null,
    val buildSrcPresent: Boolean = false,
    val configurationCacheConfigFile: Boolean = false,
    val summary: String
)
