package com.gradle.optimization.mcp.features.configcache.impl

import com.gradle.optimization.mcp.features.configcache.api.ConfigCacheInputTypeCount
import com.gradle.optimization.mcp.features.configcache.api.ConfigCacheInputViolation
import com.gradle.optimization.mcp.features.configcache.api.ConfigCacheProblem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

internal data class ParsedConfigurationCacheReport(
    val cacheAction: String?,
    val cacheActionDescription: String?,
    val requestedTasks: List<String>,
    val totalProblemCount: Int,
    val inputs: List<ConfigCacheInputViolation>,
    val problems: List<ConfigCacheProblem>,
    val inputCounts: List<ConfigCacheInputTypeCount>
)

internal object ConfigurationCacheReportParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val reportJsonRegex = Regex("""\{"diagnostics"\s*:""")
    private const val REPORT_MTIME_SKEW_MS = 2_000L

    private val actionableTypes = setOf(
        "ENVIRONMENT_VARIABLE",
        "SYSTEM_PROPERTY",
        "FILE",
        "FILE_SYSTEM_ENTRY",
        "CUSTOM_VALUE_SOURCE"
    )

    fun findNewestReport(projectDir: File, createdAfterMs: Long): File? {
        val reportRoot = File(projectDir, "build/reports/configuration-cache")
        if (!reportRoot.exists() || !reportRoot.isDirectory) return null

        val skewMs = REPORT_MTIME_SKEW_MS
        return reportRoot.walkTopDown()
            .filter { it.isFile && it.name == "configuration-cache-report.html" }
            .filter { it.lastModified() >= createdAfterMs - skewMs }
            .maxByOrNull { it.lastModified() }
            ?: reportRoot.walkTopDown()
                .filter { it.isFile && it.name == "configuration-cache-report.html" }
                .maxByOrNull { it.lastModified() }
    }

    fun extractReportPathFromOutput(output: String): String? {
        val marker = "See the complete report at "
        val start = output.indexOf(marker)
        if (start < 0) return null
        val after = output.substring(start + marker.length).lineSequence().firstOrNull()?.trim().orEmpty()
        if (after.isBlank()) return null
        return after.removePrefix("file:").removePrefix("//").trim().ifBlank { null }
    }

    fun parseReportHtml(html: String): ParsedConfigurationCacheReport? {
        val match = reportJsonRegex.find(html) ?: return null
        val jsonText = extractJsonObject(html, match.range.first) ?: return null
        val root = runCatching { json.parseToJsonElement(jsonText).jsonObject }.getOrNull() ?: return null

        val cacheAction = root["cacheAction"]?.jsonPrimitive?.content
        val cacheActionDescription = root["cacheActionDescription"]?.let { describeFragments(it) }
        val requestedTasks = root["requestedTasks"]?.jsonPrimitive?.content
            ?.split(Regex("\\s+"))
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val totalProblemCount = root["totalProblemCount"]?.jsonPrimitive?.intOrNull ?: 0

        val diagnostics = root["diagnostics"]?.jsonArray.orEmpty()
        val inputs = mutableListOf<ConfigCacheInputViolation>()
        val problems = mutableListOf<ConfigCacheProblem>()

        diagnostics.forEach { element ->
            val diagnostic = element.jsonObject
            val location = formatTrace(diagnostic["trace"])
            val documentationLink = diagnostic["documentationLink"]?.jsonPrimitive?.content

            val problemNode = diagnostic["problem"]
            if (problemNode != null) {
                val message = describeFragments(problemNode)?.takeIf { it.isNotBlank() }
                    ?: diagnostic["message"]?.jsonPrimitive?.content
                    ?: "Configuration cache problem"
                problems.add(
                    ConfigCacheProblem(
                        message = message,
                        location = location,
                        documentationLink = documentationLink
                    )
                )
                return@forEach
            }

            val inputNode = diagnostic["input"] ?: return@forEach
            val inputLabel = describeFragments(inputNode)?.trim().orEmpty()
            if (inputLabel.isBlank()) return@forEach

            val inputType = classifyInput(inputLabel)
            val inputName = extractInputName(inputLabel)
            inputs.add(
                ConfigCacheInputViolation(
                    inputName = inputName,
                    inputType = inputType,
                    location = location,
                    antiPattern = antiPatternFor(inputType),
                    recommendedRefactoring = refactoringFor(inputType),
                    documentationLink = documentationLink
                )
            )
        }

        val distinctInputs = inputs.distinctBy { it.inputType to it.inputName }
        val counts = distinctInputs
            .groupingBy { it.inputType }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { ConfigCacheInputTypeCount(it.key, it.value) }

        return ParsedConfigurationCacheReport(
            cacheAction = cacheAction,
            cacheActionDescription = cacheActionDescription,
            requestedTasks = requestedTasks,
            totalProblemCount = totalProblemCount.coerceAtLeast(problems.size),
            inputs = distinctInputs,
            problems = problems.distinctBy { it.message to it.location },
            inputCounts = counts
        )
    }

    fun selectNotableInputs(
        inputs: List<ConfigCacheInputViolation>,
        maxNotable: Int
    ): List<ConfigCacheInputViolation> {
        if (maxNotable <= 0 || inputs.isEmpty()) return emptyList()

        return inputs
            .sortedWith(
                compareByDescending<ConfigCacheInputViolation> { it.documentationLink != null }
                    .thenByDescending { it.inputType in actionableTypes }
                    .thenBy { actionableRank(it.inputType) }
                    .thenBy { it.inputName }
            )
            .filter { input ->
                input.documentationLink != null ||
                    input.inputType in actionableTypes
            }
            .take(maxNotable)
    }

    private const val RANK_ENV = 0
    private const val RANK_SYS = 1
    private const val RANK_FILE = 2
    private const val RANK_FS_ENTRY = 3
    private const val RANK_CUSTOM = 4
    private const val RANK_OTHER = 9

    private fun actionableRank(type: String): Int = when (type) {
        "ENVIRONMENT_VARIABLE" -> RANK_ENV
        "SYSTEM_PROPERTY" -> RANK_SYS
        "FILE" -> RANK_FILE
        "FILE_SYSTEM_ENTRY" -> RANK_FS_ENTRY
        "CUSTOM_VALUE_SOURCE" -> RANK_CUSTOM
        else -> RANK_OTHER
    }

    private fun classifyInput(label: String): String {
        val normalized = label.lowercase()
        return when {
            normalized.startsWith("environment variable") -> "ENVIRONMENT_VARIABLE"
            normalized.startsWith("system property") -> "SYSTEM_PROPERTY"
            normalized.startsWith("file system entry") -> "FILE_SYSTEM_ENTRY"
            normalized.startsWith("file ") || normalized == "file" -> "FILE"
            normalized.startsWith("gradle property") -> "GRADLE_PROPERTY"
            normalized.startsWith("value from custom source") -> "CUSTOM_VALUE_SOURCE"
            else -> "OTHER"
        }
    }

    private fun extractInputName(label: String): String {
        val prefixes = listOf(
            "environment variable ",
            "system property ",
            "file system entry ",
            "file ",
            "Gradle property ",
            "gradle property ",
            "value from custom source "
        )
        var name = label
        for (prefix in prefixes) {
            if (name.startsWith(prefix, ignoreCase = true)) {
                name = name.substring(prefix.length).trim()
                break
            }
        }
        return name.ifBlank { label }.trim().trimStart(',').trim()
    }

    private fun antiPatternFor(inputType: String): String? = when (inputType) {
        "ENVIRONMENT_VARIABLE" -> "Undeclared environment variable read at configuration time"
        "SYSTEM_PROPERTY" -> "Undeclared system property read at configuration time"
        "FILE", "FILE_SYSTEM_ENTRY" -> "File / file-system input read at configuration time"
        "CUSTOM_VALUE_SOURCE" -> "Custom ValueSource input at configuration time"
        else -> null
    }

    private fun refactoringFor(inputType: String): String? = when (inputType) {
        "ENVIRONMENT_VARIABLE" -> "providers.environmentVariable(\"KEY\")"
        "SYSTEM_PROPERTY" -> "providers.systemProperty(\"prop\")"
        "FILE", "FILE_SYSTEM_ENTRY" -> "providers.fileContents(...) / CC-safe file APIs"
        "CUSTOM_VALUE_SOURCE" -> "Use a configuration-cache-compatible ValueSource"
        else -> null
    }

    private fun describeFragments(node: kotlinx.serialization.json.JsonElement): String? = when (node) {
        is JsonArray -> node.joinToString("") { element ->
            val obj = element as? JsonObject ?: return@joinToString element.toString()
            val text = obj["text"]?.jsonPrimitive?.content.orEmpty()
            val name = obj["name"]?.jsonPrimitive?.content.orEmpty()
            text + name
        }.ifBlank { null }
        is JsonObject -> {
            val text = node["text"]?.jsonPrimitive?.content.orEmpty()
            val name = node["name"]?.jsonPrimitive?.content.orEmpty()
            (text + name).ifBlank { null }
        }
        else -> node.jsonPrimitive.content
    }

    private fun formatTrace(traceNode: kotlinx.serialization.json.JsonElement?): String? {
        val array = traceNode as? JsonArray ?: return null
        val parts = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val kind = obj["kind"]?.jsonPrimitive?.content
            when (kind) {
                "Project" -> obj["path"]?.jsonPrimitive?.content?.let { "project $it" }
                "BuildLogic" -> obj["location"]?.jsonPrimitive?.content
                "BuildLogicClass" -> obj["type"]?.jsonPrimitive?.content?.let { "class $it" }
                "Task" -> obj["path"]?.jsonPrimitive?.content?.let { "task $it" }
                else -> obj["location"]?.jsonPrimitive?.content
                    ?: obj["path"]?.jsonPrimitive?.content
                    ?: obj["type"]?.jsonPrimitive?.content
            }
        }
        return parts.joinToString(" > ").ifBlank { null }
    }

    private fun extractJsonObject(source: String, startIndex: Int): String? {
        var depth = 0
        var inString = false
        var escape = false
        for (i in startIndex until source.length) {
            val ch = source[i]
            if (inString) {
                when {
                    escape -> escape = false
                    ch == '\\' -> escape = true
                    ch == '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return source.substring(startIndex, i + 1)
                    }
                }
            }
        }
        return null
    }
}
