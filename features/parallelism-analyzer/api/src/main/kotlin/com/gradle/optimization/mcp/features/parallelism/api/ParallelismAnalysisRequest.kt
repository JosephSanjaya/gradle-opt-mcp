package com.gradle.optimization.mcp.features.parallelism.api

import kotlinx.serialization.Serializable

@Serializable
data class ParallelismAnalysisRequest(
    val projectDir: String,
    val tasks: List<String> = emptyList()
) {
    companion object {
        /** Prefer these over info tasks like `help` for a meaningful scheduled graph. */
        val RECOMMENDED_TASKS: List<String> = listOf("classes", "assemble", "build")
    }
}
