package com.gradle.optimization.mcp.features.runner.api

interface RunnerFeatureApi {
    fun runBuild(request: GradleRunRequest): GradleRunResult
    fun getRunLog(request: GradleRunLogRequest): GradleRunLogResult
}
