package com.gradle.optimization.mcp.features.verification.api

interface DependencyVerificationFeatureApi {
    fun verifyDependencyMetadata(request: DependencyVerificationRequest): DependencyVerificationResult
}
