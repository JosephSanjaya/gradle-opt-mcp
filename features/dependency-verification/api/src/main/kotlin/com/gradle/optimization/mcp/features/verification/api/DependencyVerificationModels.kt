package com.gradle.optimization.mcp.features.verification.api

import kotlinx.serialization.Serializable

@Serializable
data class DependencyVerificationRequest(
    val projectDir: String,
    val maxMissingChecksumComponents: Int = DEFAULT_MAX_MISSING_CHECKSUM_COMPONENTS,
    val maxTrustedArtifacts: Int = DEFAULT_MAX_TRUSTED_ARTIFACTS
) {
    companion object {
        const val DEFAULT_MAX_MISSING_CHECKSUM_COMPONENTS = 500
        const val DEFAULT_MAX_TRUSTED_ARTIFACTS = 50
    }
}

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
    val status: String,
    val verificationFileFound: Boolean,
    val verificationFilePath: String? = null,
    val verifyMetadata: Boolean = true,
    val verifySignatures: Boolean = false,
    val totalComponents: Int = 0,
    val componentsWithMissingChecksums: Int = 0,
    val trustedArtifactsCount: Int = 0,
    val components: List<VerificationComponent> = emptyList(),
    val trustedArtifacts: List<TrustedArtifact> = emptyList(),
    val truncated: Boolean = false,
    val failureReason: String? = null,
    val guidance: String? = null,
    val summary: String
) {
    companion object {
        const val STATUS_OK = "OK"
        const val STATUS_VERIFICATION_NOT_CONFIGURED = "VERIFICATION_NOT_CONFIGURED"
        const val STATUS_INVALID_XML = "INVALID_XML"
        const val STATUS_NOT_A_GRADLE_PROJECT = "NOT_A_GRADLE_PROJECT"
        const val STATUS_PROJECT_DIR_INVALID = "PROJECT_DIR_INVALID"
    }
}
