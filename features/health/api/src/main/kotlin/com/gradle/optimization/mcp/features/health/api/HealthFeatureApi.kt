package com.gradle.optimization.mcp.features.health.api

interface HealthFeatureApi {
    fun checkHealth(request: GradleHealthRequest): GradleHealthResult
}
