package com.gradle.optimization.mcp.features.parallelism.api

interface ParallelismFeatureApi {
    fun analyzeParallelizationBottlenecks(request: ParallelismAnalysisRequest): ParallelismAnalysisResult
}
