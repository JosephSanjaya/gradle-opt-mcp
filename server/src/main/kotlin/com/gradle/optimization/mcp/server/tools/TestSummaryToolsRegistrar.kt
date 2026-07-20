package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.testsummary.api.GradleTestSummaryRequest
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
            description = "Parse JUnit XML test reports across Gradle modules, aggregate pass/fail/skip metrics, " +
                "extract failure details and stack trace snippets, and check report staleness relative to " +
                "source code updates.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("projectDir", buildJsonObject { put("type", "string") })
                    put("modulePath", buildJsonObject { put("type", "string") })
                    put("includePassed", buildJsonObject { put("type", "boolean") })
                }
            )
        ) { request ->
            val args = request.params.arguments ?: JsonObject(emptyMap())
            val projectDir = args["projectDir"]?.jsonPrimitive?.content
            val modulePath = args["modulePath"]?.jsonPrimitive?.content
            val includePassed = args["includePassed"]?.jsonPrimitive?.booleanOrNull ?: false

            val result = testSummaryApi.getTestSummary(
                GradleTestSummaryRequest(
                    projectDir = projectDir,
                    modulePath = modulePath,
                    includePassed = includePassed
                )
            )

            val text = buildString {
                appendLine("Test Execution Summary:")
                appendLine("Project Dir: ${result.projectDir}")
                val countsStr = "Passed: ${result.passedCount}, Failed: ${result.failedCount}, " +
                    "Skipped: ${result.skippedCount}"
                appendLine("Total Tests: ${result.totalTests} ($countsStr)")
                appendLine("Duration: ${"%.2f".format(result.durationSeconds)} s")
                appendLine("Is Stale: ${result.isStale}")
                if (result.isStale && result.staleReason != null) {
                    appendLine("Stale Reason: ${result.staleReason}")
                }
                appendLine()

                if (result.suiteSummaries.isNotEmpty()) {
                    appendLine("Suites Analyzed (${result.suiteSummaries.size}):")
                    result.suiteSummaries.forEach { suite ->
                        val sc = "Passed: ${suite.passedCount}, Failed: ${suite.failedCount}, " +
                            "Skipped: ${suite.skippedCount}"
                        val dur = "%.2f".format(suite.durationSeconds)
                        val prefix = "  [${suite.modulePath}] ${suite.suiteName}"
                        appendLine("$prefix - Total: ${suite.totalTests} ($sc) [$dur s]")
                    }
                    appendLine()
                }

                if (result.failedTestCases.isNotEmpty()) {
                    appendLine("Failed Tests (${result.failedTestCases.size}):")
                    result.failedTestCases.forEachIndexed { index, test ->
                        val label = "${index + 1}. [FAILED] [${test.modulePath}] ${test.suiteName}.${test.testName}"
                        appendLine("$label (${test.durationSeconds} s)")
                        if (test.failureMessage != null) {
                            appendLine("   Message: ${test.failureMessage}")
                        }
                        if (test.failureType != null) {
                            appendLine("   Type: ${test.failureType}")
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
                    appendLine("Passed Tests (${result.passedTestCases.size}):")
                    result.passedTestCases.forEachIndexed { index, test ->
                        val label = "${index + 1}. [PASSED] [${test.modulePath}] ${test.suiteName}.${test.testName}"
                        appendLine("$label (${test.durationSeconds} s)")
                    }
                }
            }.trimEnd()

            CallToolResult(content = listOf(TextContent(text = text)), isError = result.failedCount > 0)
        }
    }
}
