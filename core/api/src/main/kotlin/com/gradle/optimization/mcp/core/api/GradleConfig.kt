package com.gradle.optimization.mcp.core.api

import kotlinx.serialization.Serializable

@Serializable
data class GradleConfig(
    val defaultProjectDir: String,
    val gradleVersion: String? = null
)
