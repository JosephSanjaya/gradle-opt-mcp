package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.verification.api.DependencyVerificationFeatureApi
import com.gradle.optimization.mcp.features.verification.api.DependencyVerificationRequest
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
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
            description = "Audits supply-chain integrity, untrusted artifacts, and checksum coverage " +
                "in gradle/verification-metadata.xml.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("projectDir", buildJsonObject { put("type", "string") })
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

            val result = verificationApi.verifyDependencyMetadata(DependencyVerificationRequest(projectDir))
            val text = buildString {
                appendLine(result.summary)
                if (result.components.isNotEmpty()) {
                    appendLine("\nComponents Overview:")
                    result.components.forEach { comp ->
                        val missingTag = if (comp.hasMissingChecksums) " [MISSING CHECKSUMS]" else ""
                        val artifactId = "${comp.group}:${comp.name}:${comp.version}"
                        val checksumInfo = "Checksums: ${comp.checksumCount}"
                        appendLine("- $artifactId ($checksumInfo)$missingTag")
                    }
                }
                if (result.trustedArtifacts.isNotEmpty()) {
                    appendLine("\nTrusted Artifact Overrides:")
                    result.trustedArtifacts.forEach { trusted ->
                        val reasonTag = trusted.reason?.let { " - Reason: $it" } ?: ""
                        appendLine("- ${trusted.group}:${trusted.name ?: "*"}:${trusted.version ?: "*"}$reasonTag")
                    }
                }
            }
            CallToolResult(content = listOf(TextContent(text = text.trimEnd())))
        }
    }
}
