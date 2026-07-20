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
    val totalTests: Int,
    val passedCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val durationSeconds: Double,
    val isStale: Boolean,
    val staleReason: String? = null,
    val suitesCount: Int,
    val failedTestCases: List<TestCaseDetail> = emptyList(),
    val skippedTestCases: List<TestCaseDetail> = emptyList(),
    val passedTestCases: List<TestCaseDetail> = emptyList(),
    val suiteSummaries: List<TestSuiteSummary> = emptyList()
)

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
