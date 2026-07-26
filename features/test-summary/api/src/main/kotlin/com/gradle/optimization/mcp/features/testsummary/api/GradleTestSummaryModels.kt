package com.gradle.optimization.mcp.features.testsummary.api

import kotlinx.serialization.Serializable

@Serializable
data class GradleTestSummaryRequest(
    val projectDir: String? = null,
    val modulePath: String? = null,
    val includePassed: Boolean = false
)

@Serializable
data class GradleTestSummaryResult(
    val projectDir: String,
    val reportsStatus: String,
    val guidance: String? = null,
    val totalTests: Int,
    val passedCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val durationSeconds: Double,
    val isStale: Boolean? = null,
    val staleReason: String? = null,
    val suitesCount: Int,
    val collapsedGreenSuiteCount: Int = 0,
    val failedTestCases: List<TestCaseDetail> = emptyList(),
    val skippedTestCases: List<TestCaseDetail> = emptyList(),
    val passedTestCases: List<TestCaseDetail> = emptyList(),
    val suiteSummaries: List<TestSuiteSummary> = emptyList()
) {
    companion object {
        const val STATUS_FOUND = "FOUND"
        const val STATUS_NO_REPORTS = "NO_REPORTS"
        const val STATUS_MODULE_FILTER_EMPTY = "MODULE_FILTER_EMPTY"
    }
}

@Serializable
data class TestSuiteSummary(
    val suiteName: String,
    val modulePath: String,
    val totalTests: Int,
    val passedCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val durationSeconds: Double,
    val reportTimestamp: Long
)

@Serializable
data class TestCaseDetail(
    val suiteName: String,
    val testName: String,
    val modulePath: String,
    val status: String,
    val durationSeconds: Double,
    val failureMessage: String? = null,
    val failureType: String? = null,
    val stackTraceSnippet: String? = null
)
