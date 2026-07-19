package com.gradle.optimization.mcp.features.dependencyinsight.api

interface DependencyInsightFeatureApi {
    fun getDependencyInsight(request: DependencyInsightRequest): DependencyInsightResult
}
