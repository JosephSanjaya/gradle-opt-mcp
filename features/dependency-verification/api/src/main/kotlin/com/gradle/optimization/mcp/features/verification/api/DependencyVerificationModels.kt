package com.gradle.optimization.mcp.features.verification.api

import kotlinx.serialization.Serializable

@Serializable
data class DependencyVerificationRequest(
    val projectDir: String
)

@Serializable
data class VerificationChecksum(
    val type: String,
    val value: String
)

@Serializable
data class VerificationComponent(
    val group: String,
    val name: String,
    val version: String,
    val checksumCount: Int,
    val hasMissingChecksums: Boolean
)

@Serializable
data class TrustedArtifact(
    val group: String,
    val name: String? = null,
    val version: String? = null,
    val fileName: String? = null,
    val reason: String? = null
)

@Serializable
data class DependencyVerificationResult(
    val projectDir: String,
    val verificationFileFound: Boolean,
    val verificationFilePath: String? = null,
    val verifyMetadata: Boolean = true,
    val verifySignatures: Boolean = false,
    val totalComponents: Int = 0,
    val componentsWithMissingChecksums: Int = 0,
    val trustedArtifactsCount: Int = 0,
    val components: List<VerificationComponent> = emptyList(),
    val trustedArtifacts: List<TrustedArtifact> = emptyList(),
    val summary: String
)
