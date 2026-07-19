package com.gradle.optimization.mcp.features.buildscan.api

interface BuildScanFeatureApi {
    fun analyzeBuildScan(request: BuildScanRequest): BuildScanResult
}
