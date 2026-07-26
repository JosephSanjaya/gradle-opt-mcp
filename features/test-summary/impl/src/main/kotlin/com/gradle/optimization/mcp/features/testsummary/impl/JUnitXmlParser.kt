package com.gradle.optimization.mcp.features.testsummary.impl

import com.gradle.optimization.mcp.features.testsummary.api.TestCaseDetail
import com.gradle.optimization.mcp.features.testsummary.api.TestSuiteSummary
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

internal data class ParsedSuiteResult(
    val summary: TestSuiteSummary,
    val testCases: List<TestCaseDetail>
)

internal object JUnitXmlParser {
    fun parseFile(xmlFile: File, targetDir: File): List<ParsedSuiteResult> {
        val modulePath = extractModulePath(xmlFile, targetDir)
        val reportTimestamp = xmlFile.lastModified()

        return runCatching {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(xmlFile)
            doc.documentElement.normalize()

            val suiteNodes = mutableListOf<Element>()
            if (doc.documentElement.nodeName == "testsuite") {
                suiteNodes.add(doc.documentElement)
            } else if (doc.documentElement.nodeName == "testsuites") {
                val nodeList = doc.documentElement.getElementsByTagName("testsuite")
                for (i in 0 until nodeList.length) {
                    val node = nodeList.item(i)
                    if (node is Element) {
                        suiteNodes.add(node)
                    }
                }
            }

            suiteNodes.map { parseSuiteElement(it, modulePath, reportTimestamp) }
        }.getOrElse { emptyList() }
    }

    private fun parseSuiteElement(
        suiteElement: Element,
        modulePath: String,
        reportTimestamp: Long
    ): ParsedSuiteResult {
        val suiteName = suiteElement.getAttribute("name").ifEmpty { "UnknownSuite" }
        val timeAttr = suiteElement.getAttribute("time").toDoubleOrNull() ?: 0.0

        val caseNodes = suiteElement.getElementsByTagName("testcase")
        val cases = mutableListOf<TestCaseDetail>()

        for (i in 0 until caseNodes.length) {
            val caseNode = caseNodes.item(i)
            if (caseNode is Element) {
                cases.add(parseCaseElement(caseNode, suiteName, modulePath))
            }
        }

        val passedCount = cases.count { it.status == "PASSED" }
        val failedCount = cases.count { it.status == "FAILED" }
        val skippedCount = cases.count { it.status == "SKIPPED" }

        val summary = TestSuiteSummary(
            suiteName = suiteName,
            modulePath = modulePath,
            totalTests = cases.size,
            passedCount = passedCount,
            failedCount = failedCount,
            skippedCount = skippedCount,
            durationSeconds = timeAttr,
            reportTimestamp = reportTimestamp
        )

        return ParsedSuiteResult(summary = summary, testCases = cases)
    }

    private fun parseCaseElement(
        caseElement: Element,
        defaultSuiteName: String,
        modulePath: String
    ): TestCaseDetail {
        val name = caseElement.getAttribute("name").ifEmpty { "unnamed" }
        val classname = caseElement.getAttribute("classname").ifEmpty { defaultSuiteName }
        val time = caseElement.getAttribute("time").toDoubleOrNull() ?: 0.0

        var status = "PASSED"
        var failureMsg: String? = null
        var failureType: String? = null
        var snippet: String? = null

        val failureNodes = caseElement.getElementsByTagName("failure")
        val errorNodes = caseElement.getElementsByTagName("error")
        val skippedNodes = caseElement.getElementsByTagName("skipped")
        val ignoredNodes = caseElement.getElementsByTagName("ignored")

        if (failureNodes.length > 0 || errorNodes.length > 0) {
            status = "FAILED"
            val itemNode = if (failureNodes.length > 0) failureNodes.item(0) else errorNodes.item(0)
            val failElem = itemNode as Element
            failureMsg = failElem.getAttribute("message").ifEmpty { failElem.textContent?.trim() }
            failureType = failElem.getAttribute("type").ifEmpty { failElem.tagName }
            val fullText = failElem.textContent?.trim() ?: ""
            snippet = buildStackSnippet(fullText)
        } else if (skippedNodes.length > 0 || ignoredNodes.length > 0) {
            status = "SKIPPED"
        }

        return TestCaseDetail(
            suiteName = classname,
            testName = name,
            modulePath = modulePath,
            status = status,
            durationSeconds = time,
            failureMessage = failureMsg,
            failureType = failureType,
            stackTraceSnippet = snippet
        )
    }

    fun extractModulePath(xmlFile: File, targetDir: File): String {
        val relPath = xmlFile.relativeToOrNull(targetDir)?.path ?: return ":"
        val buildIndex = relPath.indexOf("/build/test-results/")
        if (buildIndex < 0) {
            val altIndex = relPath.indexOf("build/test-results/")
            if (altIndex == 0) return ":"
            if (altIndex > 0) {
                val rawModule = relPath.substring(0, altIndex - 1)
                return ":" + rawModule.replace('/', ':')
            }
            return ":"
        }
        val rawModule = relPath.substring(0, buildIndex)
        return if (rawModule.isEmpty()) ":" else ":" + rawModule.replace('/', ':')
    }

    internal fun buildStackSnippet(fullText: String): String {
        val lines = fullText.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return ""

        val header = mutableListOf<String>()
        val frames = mutableListOf<String>()
        for (line in lines) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("at ")) {
                frames.add(trimmed)
            } else if (frames.isEmpty()) {
                header.add(line.trim())
            }
        }

        val appFrames = frames.filterNot { isNoiseFrame(it) }
        val selectedFrames = (if (appFrames.isNotEmpty()) appFrames else frames).take(MAX_APP_FRAMES)
        val selectedHeader = header.take(MAX_HEADER_LINES)
        return (selectedHeader + selectedFrames).joinToString("\n")
    }

    private fun isNoiseFrame(frame: String): Boolean {
        val target = frame.removePrefix("at ").trim()
        return NOISE_PREFIXES.any { target.startsWith(it) }
    }

    private val NOISE_PREFIXES = listOf(
        "org.junit.",
        "junit.framework.",
        "jdk.internal.",
        "java.lang.reflect.",
        "sun.reflect.",
        "org.opentest4j."
    )

    private const val MAX_HEADER_LINES = 2
    private const val MAX_APP_FRAMES = 6
}
