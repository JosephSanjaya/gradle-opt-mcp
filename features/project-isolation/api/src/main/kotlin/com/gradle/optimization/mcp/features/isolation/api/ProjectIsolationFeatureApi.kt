package com.gradle.optimization.mcp.features.isolation.api

interface ProjectIsolationFeatureApi {
    fun checkProjectIsolation(request: IsolationCheckRequest): IsolationCheckResult
}
