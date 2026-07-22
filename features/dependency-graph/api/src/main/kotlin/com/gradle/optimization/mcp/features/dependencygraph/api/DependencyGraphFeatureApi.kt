package com.gradle.optimization.mcp.features.dependencygraph.api

interface DependencyGraphFeatureApi {
    fun getDependencyGraph(request: GradleDepsRequest): GradleDepsResult
}
