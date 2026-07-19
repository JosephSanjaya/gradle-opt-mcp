package com.gradle.optimization.mcp.features.verification.impl

import com.gradle.optimization.mcp.features.verification.api.DependencyVerificationFeatureApi
import com.gradle.optimization.mcp.features.verification.api.DependencyVerificationRequest
import com.gradle.optimization.mcp.features.verification.api.DependencyVerificationResult
import com.gradle.optimization.mcp.features.verification.api.TrustedArtifact
import com.gradle.optimization.mcp.features.verification.api.VerificationComponent
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.koin.core.annotation.Single
import org.w3c.dom.Element

@Single
class DependencyVerificationFeatureImpl : DependencyVerificationFeatureApi {
    override fun verifyDependencyMetadata(request: DependencyVerificationRequest): DependencyVerificationResult {
        val projectDir = File(request.projectDir)
        require(projectDir.exists() && projectDir.isDirectory) {
            "Project directory does not exist or is not a directory: ${request.projectDir}"
        }

        val verificationFile = File(projectDir, "gradle/verification-metadata.xml")
        if (!verificationFile.exists()) {
            return DependencyVerificationResult(
                projectDir = request.projectDir,
                verificationFileFound = false,
                summary = "No gradle/verification-metadata.xml found in project directory. " +
                    "Dependency verification is not configured."
            )
        }

        return parseVerificationMetadata(projectDir, verificationFile)
    }

    private fun parseVerificationMetadata(projectDir: File, verificationFile: File): DependencyVerificationResult {
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(verificationFile)
        doc.documentElement.normalize()

        val root = doc.documentElement
        var verifyMetadata = true
        var verifySignatures = false

        val configNodes = root.getElementsByTagName("configuration")
        if (configNodes.length > 0) {
            val configElem = configNodes.item(0) as Element
            val verifyMetadataNodes = configElem.getElementsByTagName("verify-metadata")
            if (verifyMetadataNodes.length > 0) {
                verifyMetadata = verifyMetadataNodes.item(0).textContent.trim().lowercase() == "true"
            }
            val verifySignaturesNodes = configElem.getElementsByTagName("verify-signatures")
            if (verifySignaturesNodes.length > 0) {
                verifySignatures = verifySignaturesNodes.item(0).textContent.trim().lowercase() == "true"
            }
        }

        val trustedArtifacts = mutableListOf<TrustedArtifact>()
        val trustedNodes = root.getElementsByTagName("trusted-artifact")
        for (i in 0 until trustedNodes.length) {
            val elem = trustedNodes.item(i) as Element
            val group = elem.getAttribute("group")
            val name = elem.getAttribute("name").ifEmpty { null }
            val version = elem.getAttribute("version").ifEmpty { null }
            val fileName = elem.getAttribute("file").ifEmpty { null }
            val reason = elem.getAttribute("reason").ifEmpty { null }
            if (group.isNotEmpty()) {
                trustedArtifacts.add(
                    TrustedArtifact(
                        group = group,
                        name = name,
                        version = version,
                        fileName = fileName,
                        reason = reason
                    )
                )
            }
        }

        val components = mutableListOf<VerificationComponent>()
        var missingChecksumCount = 0

        val componentNodes = root.getElementsByTagName("component")
        for (i in 0 until componentNodes.length) {
            val elem = componentNodes.item(i) as Element
            val group = elem.getAttribute("group")
            val name = elem.getAttribute("name")
            val version = elem.getAttribute("version")

            val artifactNodes = elem.getElementsByTagName("artifact")
            var checksumCount = 0
            var hasMissingChecksum = artifactNodes.length == 0

            for (j in 0 until artifactNodes.length) {
                val artifactElem = artifactNodes.item(j) as Element
                val sha256Nodes = artifactElem.getElementsByTagName("sha256")
                val sha512Nodes = artifactElem.getElementsByTagName("sha512")
                val pgpNodes = artifactElem.getElementsByTagName("pgp")

                val totalArtifactChecksums = sha256Nodes.length + sha512Nodes.length + pgpNodes.length
                checksumCount += totalArtifactChecksums

                if (totalArtifactChecksums == 0) {
                    hasMissingChecksum = true
                }
            }

            if (hasMissingChecksum) {
                missingChecksumCount++
            }

            components.add(
                VerificationComponent(
                    group = group,
                    name = name,
                    version = version,
                    checksumCount = checksumCount,
                    hasMissingChecksums = hasMissingChecksum
                )
            )
        }

        val summary = buildString {
            append("Dependency Verification Metadata Audit:\n")
            append("- Verification file: ${verificationFile.relativeTo(projectDir).path}\n")
            append("- Verify Metadata: $verifyMetadata | Verify Signatures: $verifySignatures\n")
            append("- Total Components: ${components.size}\n")
            append("- Components with Missing Checksums: $missingChecksumCount\n")
            append("- Trusted Artifact Overrides: ${trustedArtifacts.size}\n")
        }

        return DependencyVerificationResult(
            projectDir = projectDir.absolutePath,
            verificationFileFound = true,
            verificationFilePath = verificationFile.absolutePath,
            verifyMetadata = verifyMetadata,
            verifySignatures = verifySignatures,
            totalComponents = components.size,
            componentsWithMissingChecksums = missingChecksumCount,
            trustedArtifactsCount = trustedArtifacts.size,
            components = components,
            trustedArtifacts = trustedArtifacts,
            summary = summary
        )
    }
}
