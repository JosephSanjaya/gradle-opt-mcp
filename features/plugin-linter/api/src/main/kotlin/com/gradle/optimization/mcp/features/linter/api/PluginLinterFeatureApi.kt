package com.gradle.optimization.mcp.features.linter.api

interface PluginLinterFeatureApi {
    fun lintPlugins(request: PluginLinterRequest): PluginLinterResult
}
