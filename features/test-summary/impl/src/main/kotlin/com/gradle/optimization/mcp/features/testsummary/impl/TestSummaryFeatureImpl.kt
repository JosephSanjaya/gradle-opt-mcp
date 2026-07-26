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

        val allXmlFiles = findTestXmlFiles(targetDir)
        val reqModulePath = request.modulePath
        val filteredXmlFiles = filterXmlByModule(allXmlFiles, targetDir, reqModulePath)

        if (allXmlFiles.isEmpty()) {
            return emptyResult(
                projectDir = targetDir.absolutePath,
                status = GradleTestSummaryResult.STATUS_NO_REPORTS,
                guidance = GUIDANCE_NO_REPORTS
            )
        }
        if (filteredXmlFiles.isEmpty()) {
            val moduleLabel = reqModulePath?.trim().orEmpty()
            return emptyResult(
                projectDir = targetDir.absolutePath,
                status = GradleTestSummaryResult.STATUS_MODULE_FILTER_EMPTY,
                guidance = "No JUnit XML reports matched modulePath='$moduleLabel'. " +
                    "Run tests for that module (e.g. gradle_run tasks=[\"$moduleLabel:test\"]) " +
                    "or omit modulePath. This tool only reads existing reports — it does not execute tests."
            )
        }

        val staleness = StalenessChecker.checkStaleness(targetDir, filteredXmlFiles)
        val parsedResults = filteredXmlFiles.flatMap { file ->
            JUnitXmlParser.parseFile(file, targetDir)
        }

        val allSuites = parsedResults.map { it.summary }
        val allTestCases = parsedResults.flatMap { it.testCases }

        val failedTestCases = allTestCases.filter { it.status == "FAILED" }
        val skippedTestCases = allTestCases.filter { it.status == "SKIPPED" }
        val passedTestCases = if (request.includePassed) {
            allTestCases.filter { it.status == "PASSED" }.take(MAX_PASSED_DETAILS)
        } else {
            emptyList()
        }

        val failedCount = failedTestCases.size
        val (visibleSuites, collapsedGreen) = if (failedCount > 0) {
            val green = allSuites.filter { it.failedCount == 0 && it.skippedCount == 0 }
            allSuites.filter { it.failedCount > 0 || it.skippedCount > 0 } to green.size
        } else {
            allSuites to 0
        }

        return GradleTestSummaryResult(
            projectDir = targetDir.absolutePath,
            reportsStatus = GradleTestSummaryResult.STATUS_FOUND,
            guidance = null,
            totalTests = allTestCases.size,
            passedCount = allTestCases.count { it.status == "PASSED" },
            failedCount = failedCount,
            skippedCount = skippedTestCases.size,
            durationSeconds = allSuites.sumOf { it.durationSeconds },
            isStale = staleness.isStale,
            staleReason = staleness.staleReason,
            suitesCount = allSuites.size,
            collapsedGreenSuiteCount = collapsedGreen,
            failedTestCases = failedTestCases,
            skippedTestCases = skippedTestCases,
            passedTestCases = passedTestCases,
            suiteSummaries = visibleSuites
        )
    }

    private fun emptyResult(
        projectDir: String,
        status: String,
        guidance: String
    ): GradleTestSummaryResult = GradleTestSummaryResult(
        projectDir = projectDir,
        reportsStatus = status,
        guidance = guidance,
        totalTests = 0,
        passedCount = 0,
        failedCount = 0,
        skippedCount = 0,
        durationSeconds = 0.0,
        isStale = null,
        staleReason = null,
        suitesCount = 0
    )

    private fun filterXmlByModule(
        xmlFiles: List<File>,
        targetDir: File,
        modulePath: String?
    ): List<File> {
        if (modulePath.isNullOrBlank()) return xmlFiles
        val reqModule = modulePath.trim()
        return xmlFiles.filter { file ->
            val module = JUnitXmlParser.extractModulePath(file, targetDir)
            module == reqModule || module.startsWith("$reqModule:")
        }
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

    companion object {
        private const val MAX_PASSED_DETAILS = 20
        private const val GUIDANCE_NO_REPORTS =
            "No JUnit XML reports found under build/test-results. " +
                "Run tests first (e.g. gradle_run with tasks=[\"test\"]). " +
                "This tool only reads existing reports — it does not execute tests."
    }
}
