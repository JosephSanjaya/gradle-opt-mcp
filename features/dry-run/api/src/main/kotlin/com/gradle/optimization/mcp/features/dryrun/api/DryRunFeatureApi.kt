package com.gradle.optimization.mcp.features.dryrun.api

interface DryRunFeatureApi {
    fun analyzeDryRun(request: DryRunRequest): DryRunResult
}
