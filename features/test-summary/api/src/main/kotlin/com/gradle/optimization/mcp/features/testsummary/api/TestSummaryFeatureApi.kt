package com.gradle.optimization.mcp.features.testsummary.api

interface TestSummaryFeatureApi {
    fun getTestSummary(request: GradleTestSummaryRequest): GradleTestSummaryResult
}
