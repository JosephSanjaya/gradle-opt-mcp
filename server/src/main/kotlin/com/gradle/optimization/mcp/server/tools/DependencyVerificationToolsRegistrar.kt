package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.verification.api.DependencyVerificationFeatureApi
import com.gradle.optimization.mcp.features.verification.api.DependencyVerificationRequest
import com.gradle.optimization.mcp.features.verification.api.DependencyVerificationResult
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class DependencyVerificationToolsRegistrar(
    @Provided private val verificationApi: DependencyVerificationFeatureApi
) : McpToolsRegistrar {
    override fun register(server: Server) {
        server.addTool(
            name = "verify_dependency_metadata",
            description = "Static audit of gradle/verification-metadata.xml only: checksum coverage " +
                "gaps and trusted-artifact overrides. Does not resolve declared dependencies, " +
                "validate keyrings, or run Gradle verification. Caps missing-checksum components " +
                "(default 500) and trusted overrides (default 50); sets truncated when capped. " +
                "Missing metadata returns VERIFICATION_NOT_CONFIGURED with --write-verification-metadata guidance.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("projectDir", buildJsonObject { put("type", "string") })
                    put("maxMissingChecksumComponents", buildJsonObject { put("type", "integer") })
                    put("maxTrustedArtifacts", buildJsonObject { put("type", "integer") })
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
            val maxMissing = args["maxMissingChecksumComponents"]?.jsonPrimitive?.intOrNull
                ?: DependencyVerificationRequest.DEFAULT_MAX_MISSING_CHECKSUM_COMPONENTS
            val maxTrusted = args["maxTrustedArtifacts"]?.jsonPrimitive?.intOrNull
                ?: DependencyVerificationRequest.DEFAULT_MAX_TRUSTED_ARTIFACTS

            val result = runCatching {
                verificationApi.verifyDependencyMetadata(
                    DependencyVerificationRequest(
                        projectDir = projectDir,
                        maxMissingChecksumComponents = maxMissing,
                        maxTrustedArtifacts = maxTrusted
                    )
                )
            }.getOrElse { error ->
                return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Error: ${error.message ?: error}")),
                    isError = true
                )
            }

            CallToolResult(
                content = listOf(TextContent(text = formatResult(result))),
                isError = result.status != DependencyVerificationResult.STATUS_OK
            )
        }
    }

    private fun formatResult(result: DependencyVerificationResult): String =
        buildString {
            appendLine(result.summary)
            appendLine("Status: ${result.status}")
            appendLine("Project Dir: ${result.projectDir}")
            appendLine("Verification File Found: ${result.verificationFileFound}")
            if (result.verificationFilePath != null) {
                appendLine("Verification File: ${result.verificationFilePath}")
            }
            if (!result.failureReason.isNullOrBlank()) {
                appendLine("Failure Reason: ${result.failureReason}")
            }
            if (!result.guidance.isNullOrBlank()) {
                appendLine("Guidance: ${result.guidance}")
            }
            if (result.status != DependencyVerificationResult.STATUS_OK) {
                return@buildString
            }

            appendLine("Verify Metadata: ${result.verifyMetadata}")
            appendLine("Verify Signatures: ${result.verifySignatures}")
            appendLine("Total Components: ${result.totalComponents}")
            appendLine("Missing Checksum Components: ${result.componentsWithMissingChecksums}")
            appendLine("Trusted Artifact Overrides: ${result.trustedArtifactsCount}")
            appendLine("Truncated: ${result.truncated}")

            if (result.components.isNotEmpty()) {
                appendLine()
                val label = if (result.components.size < result.componentsWithMissingChecksums) {
                    "Missing Checksum Components " +
                        "(${result.components.size} of ${result.componentsWithMissingChecksums}, truncated):"
                } else {
                    "Missing Checksum Components (${result.components.size}):"
                }
                appendLine(label)
                result.components.forEach { comp ->
                    val artifactId = "${comp.group}:${comp.name}:${comp.version}"
                    appendLine("- $artifactId (Checksums: ${comp.checksumCount})")
                }
            }

            if (result.trustedArtifacts.isNotEmpty()) {
                appendLine()
                val label = if (result.trustedArtifacts.size < result.trustedArtifactsCount) {
                    "Trusted Artifact Overrides " +
                        "(${result.trustedArtifacts.size} of ${result.trustedArtifactsCount}, truncated):"
                } else {
                    "Trusted Artifact Overrides (${result.trustedArtifacts.size}):"
                }
                appendLine(label)
                result.trustedArtifacts.forEach { trusted ->
                    val reasonTag = trusted.reason?.let { " - Reason: $it" } ?: ""
                    appendLine("- ${trusted.group}:${trusted.name ?: "*"}:${trusted.version ?: "*"}$reasonTag")
                }
            }
        }.trimEnd()
}
