package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.testsummary.api.GradleTestSummaryRequest
import com.gradle.optimization.mcp.features.testsummary.api.GradleTestSummaryResult
import com.gradle.optimization.mcp.features.testsummary.api.TestSummaryFeatureApi
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class TestSummaryToolsRegistrar(
    @Provided private val testSummaryApi: TestSummaryFeatureApi
) : McpToolsRegistrar {
    override fun register(server: Server) {
        server.addTool(
            name = "gradle_test_summary",
            description = "Read existing JUnit XML under build/test-results (does not run tests). " +
                "Returns pass/fail/skip counts, failure messages, app-frame stack snippets " +
                "(skips junit/jdk/reflect frames), module paths, and staleness vs sources. " +
                "projectDir required. Optional modulePath filters modules; includePassed adds capped passed cases. " +
                "When reports are missing, guidance tells you to run tests via gradle_run.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put(
                        "projectDir",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Absolute path to the Gradle project root")
                        }
                    )
                    put(
                        "modulePath",
                        buildJsonObject {
                            put("type", "string")
                            put(
                                "description",
                                "Optional Gradle module path filter (e.g. :features:runner); " +
                                    "matches exact or nested :module:child"
                            )
                        }
                    )
                    put(
                        "includePassed",
                        buildJsonObject {
                            put("type", "boolean")
                            put(
                                "description",
                                "When true, include a capped list of passed test cases (default false)"
                            )
                        }
                    )
                },
                required = listOf("projectDir")
            )
        ) { request ->
            val args = request.params.arguments ?: JsonObject(emptyMap())
            val projectDir = args["projectDir"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Error: projectDir parameter is required")),
                    isError = true
                )
            val modulePath = args["modulePath"]?.jsonPrimitive?.content
            val includePassed = args["includePassed"]?.jsonPrimitive?.booleanOrNull ?: false

            val result = testSummaryApi.getTestSummary(
                GradleTestSummaryRequest(
                    projectDir = projectDir,
                    modulePath = modulePath,
                    includePassed = includePassed
                )
            )

            CallToolResult(
                content = listOf(TextContent(text = formatResult(result, includePassed))),
                isError = result.failedCount > 0 ||
                    result.reportsStatus != GradleTestSummaryResult.STATUS_FOUND
            )
        }
    }

    private fun formatResult(result: GradleTestSummaryResult, includePassed: Boolean): String =
        buildString {
            appendLine("Test Execution Summary:")
            appendLine("Project Dir: ${result.projectDir}")
            appendLine("Reports Status: ${result.reportsStatus}")
            val guidance = result.guidance
            if (guidance != null) {
                appendLine("Guidance: $guidance")
            }

            if (result.reportsStatus != GradleTestSummaryResult.STATUS_FOUND) {
                appendLine("Total Tests: 0 (Passed: 0, Failed: 0, Skipped: 0)")
                return@buildString
            }

            val countsStr = "Passed: ${result.passedCount}, Failed: ${result.failedCount}, " +
                "Skipped: ${result.skippedCount}"
            appendLine("Total Tests: ${result.totalTests} ($countsStr)")
            appendLine("Duration: ${"%.2f".format(result.durationSeconds)} s")
            val isStale = result.isStale
            if (isStale != null) {
                appendLine("Is Stale: $isStale")
                val staleReason = result.staleReason
                if (isStale && staleReason != null) {
                    appendLine("Stale Reason: $staleReason")
                }
            }
            appendLine()

            if (result.suiteSummaries.isNotEmpty()) {
                appendLine("Suites Analyzed (${result.suiteSummaries.size}" +
                    if (result.collapsedGreenSuiteCount > 0) {
                        "; ${result.collapsedGreenSuiteCount} all-green suites collapsed"
                    } else {
                        ""
                    } +
                    "):")
                result.suiteSummaries.forEach { suite ->
                    val sc = "Passed: ${suite.passedCount}, Failed: ${suite.failedCount}, " +
                        "Skipped: ${suite.skippedCount}"
                    val dur = "%.2f".format(suite.durationSeconds)
                    val prefix = "  [${suite.modulePath}] ${suite.suiteName}"
                    appendLine("$prefix - Total: ${suite.totalTests} ($sc) [$dur s]")
                }
                appendLine()
            } else if (result.collapsedGreenSuiteCount > 0) {
                appendLine(
                    "Suites: ${result.collapsedGreenSuiteCount} all-green suites collapsed " +
                        "(no failures/skips to list)."
                )
                appendLine()
            }

            if (result.failedTestCases.isNotEmpty()) {
                appendLine("Failed Tests (${result.failedTestCases.size}):")
                result.failedTestCases.forEachIndexed { index, test ->
                    val label = "${index + 1}. [FAILED] [${test.modulePath}] ${test.suiteName}.${test.testName}"
                    appendLine("$label (${test.durationSeconds} s)")
                    val failureMessage = test.failureMessage
                    if (failureMessage != null) {
                        appendLine("   Message: $failureMessage")
                    }
                    val failureType = test.failureType
                    if (failureType != null) {
                        appendLine("   Type: $failureType")
                    }
                    val snippet = test.stackTraceSnippet
                    if (snippet != null) {
                        appendLine("   Snippet:")
                        snippet.lines().forEach { line ->
                            appendLine("     $line")
                        }
                    }
                }
                appendLine()
            }

            if (result.skippedTestCases.isNotEmpty()) {
                appendLine("Skipped Tests (${result.skippedTestCases.size}):")
                result.skippedTestCases.forEachIndexed { index, test ->
                    val label = "${index + 1}. [SKIPPED] [${test.modulePath}] ${test.suiteName}.${test.testName}"
                    appendLine(label)
                }
                appendLine()
            }

            if (includePassed && result.passedTestCases.isNotEmpty()) {
                appendLine("Passed Tests (${result.passedTestCases.size}, capped):")
                result.passedTestCases.forEachIndexed { index, test ->
                    val label = "${index + 1}. [PASSED] [${test.modulePath}] ${test.suiteName}.${test.testName}"
                    appendLine("$label (${test.durationSeconds} s)")
                }
            }
        }.trimEnd()
}
