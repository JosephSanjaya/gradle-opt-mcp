package com.gradle.optimization.mcp.features.cacheprofiler.api

import kotlinx.serialization.Serializable

@Serializable
data class CacheProfileRequest(
    val projectDir: String,
    val limit: Int = 10
)

@Serializable
data class CacheInvalidationEntry(
    val entryId: String,
    val timestamp: Long,
    val formattedTime: String,
    val status: String,
    val invalidationReasons: List<String> = emptyList(),
    val inputDiffSummary: String? = null
)

@Serializable
data class CacheProfileResult(
    val projectDir: String,
    val totalEntriesFound: Int,
    val entries: List<CacheInvalidationEntry>,
    val summary: String
)
