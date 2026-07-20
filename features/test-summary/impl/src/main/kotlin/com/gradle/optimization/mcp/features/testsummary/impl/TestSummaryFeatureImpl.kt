package com.gradle.optimization.mcp.features.testsummary.impl

import com.gradle.optimization.mcp.core.api.GradleConfig
import com.gradle.optimization.mcp.features.testsummary.api.GradleTestSummaryRequest
import com.gradle.optimization.mcp.features.testsummary.api.GradleTestSummaryResult
import com.gradle.optimization.mcp.features.testsummary.api.TestSummaryFeatureApi
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File

@Single
class TestSummaryFeatureImpl(
    @Provided private val config: GradleConfig
) : TestSummaryFeatureApi {
    override fun getTestSummary(request: GradleTestSummaryRequest): GradleTestSummaryResult {
        val targetDirPath = request.projectDir ?: config.defaultProjectDir
        val targetDir = File(targetDirPath)
        require(targetDir.exists()) { "Project directory does not exist: $targetDirPath" }

        val xmlFiles = findTestXmlFiles(targetDir)
        val staleness = StalenessChecker.checkStaleness(targetDir, xmlFiles)

        val parsedResults = xmlFiles.flatMap { file ->
            JUnitXmlParser.parseFile(file, targetDir)
        }

        val reqModulePath = request.modulePath
        val filteredResults = if (!reqModulePath.isNullOrBlank()) {
            val reqModule = reqModulePath.trim()
            parsedResults.filter { res ->
                res.summary.modulePath == reqModule || res.summary.modulePath.startsWith("$reqModule:")
            }
        } else {
            parsedResults
        }

        val allSuites = filteredResults.map { it.summary }
        val allTestCases = filteredResults.flatMap { it.testCases }

        val failedTestCases = allTestCases.filter { it.status == "FAILED" }
        val skippedTestCases = allTestCases.filter { it.status == "SKIPPED" }
        val passedTestCases = if (request.includePassed) {
            allTestCases.filter { it.status == "PASSED" }
        } else {
            emptyList()
        }

        val totalTests = allTestCases.size
        val passedCount = allTestCases.count { it.status == "PASSED" }
        val failedCount = failedTestCases.size
        val skippedCount = skippedTestCases.size
        val totalDuration = allSuites.sumOf { it.durationSeconds }

        return GradleTestSummaryResult(
            projectDir = targetDir.absolutePath,
            totalTests = totalTests,
            passedCount = passedCount,
            failedCount = failedCount,
            skippedCount = skippedCount,
            durationSeconds = totalDuration,
            isStale = staleness.isStale,
            staleReason = staleness.staleReason,
            suitesCount = allSuites.size,
            failedTestCases = failedTestCases,
            skippedTestCases = skippedTestCases,
            passedTestCases = passedTestCases,
            suiteSummaries = allSuites
        )
    }

    private fun findTestXmlFiles(targetDir: File): List<File> {
        val results = mutableListOf<File>()
        targetDir.walkTopDown()
            .onEnter { dir ->
                dir.name != ".gradle" && !dir.name.startsWith(".")
            }
            .filter { file ->
                file.isFile &&
                    file.name.startsWith("TEST-") &&
                    file.name.endsWith(".xml") &&
                    file.path.contains("test-results")
            }
            .forEach { results.add(it) }
        return results
    }
}
