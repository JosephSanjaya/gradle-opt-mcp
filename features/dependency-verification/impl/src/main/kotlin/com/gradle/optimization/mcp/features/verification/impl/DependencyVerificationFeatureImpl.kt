package com.gradle.optimization.mcp.features.verification.impl

import com.gradle.optimization.mcp.features.verification.api.DependencyVerificationFeatureApi
import com.gradle.optimization.mcp.features.verification.api.DependencyVerificationRequest
import com.gradle.optimization.mcp.features.verification.api.DependencyVerificationResult
import com.gradle.optimization.mcp.features.verification.api.TrustedArtifact
import com.gradle.optimization.mcp.features.verification.api.VerificationComponent
import java.io.File
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.koin.core.annotation.Single
import org.w3c.dom.Element
import org.xml.sax.SAXException

@Single
class DependencyVerificationFeatureImpl : DependencyVerificationFeatureApi {
    override fun verifyDependencyMetadata(request: DependencyVerificationRequest): DependencyVerificationResult {
        require(request.projectDir.isNotBlank()) { "projectDir is required" }
        val projectDir = File(request.projectDir).canonicalFile
        if (!projectDir.isDirectory) {
            return DependencyVerificationResult(
                projectDir = request.projectDir,
                status = DependencyVerificationResult.STATUS_PROJECT_DIR_INVALID,
                verificationFileFound = false,
                failureReason = "Project directory does not exist or is not a directory: ${request.projectDir}",
                summary = "Invalid projectDir: ${request.projectDir}"
            )
        }

        if (!looksLikeGradleProject(projectDir)) {
            return DependencyVerificationResult(
                projectDir = projectDir.path,
                status = DependencyVerificationResult.STATUS_NOT_A_GRADLE_PROJECT,
                verificationFileFound = false,
                failureReason = "Not a Gradle project (missing settings/build script or wrapper): ${projectDir.path}",
                summary = "Not a Gradle project at ${projectDir.path}"
            )
        }

        val verificationFile = File(projectDir, "gradle/verification-metadata.xml")
        if (!verificationFile.isFile) {
            return DependencyVerificationResult(
                projectDir = projectDir.path,
                status = DependencyVerificationResult.STATUS_VERIFICATION_NOT_CONFIGURED,
                verificationFileFound = false,
                guidance = GUIDANCE_NOT_CONFIGURED,
                summary = buildString {
                    append("Status: ${DependencyVerificationResult.STATUS_VERIFICATION_NOT_CONFIGURED}. ")
                    append("No gradle/verification-metadata.xml at ${projectDir.path}. ")
                    append(GUIDANCE_NOT_CONFIGURED)
                }
            )
        }

        return parseVerificationMetadata(projectDir, verificationFile, request)
    }

    private fun parseVerificationMetadata(
        projectDir: File,
        verificationFile: File,
        request: DependencyVerificationRequest
    ): DependencyVerificationResult {
        val doc = try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            dBuilder.parse(verificationFile).also { it.documentElement.normalize() }
        } catch (error: SAXException) {
            return invalidXmlResult(projectDir, verificationFile, error)
        } catch (error: IOException) {
            return invalidXmlResult(projectDir, verificationFile, error)
        } catch (error: ParserConfigurationException) {
            return invalidXmlResult(projectDir, verificationFile, error)
        }

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

        val allTrusted = mutableListOf<TrustedArtifact>()
        val trustedNodes = root.getElementsByTagName("trusted-artifact")
        for (i in 0 until trustedNodes.length) {
            val elem = trustedNodes.item(i) as Element
            val group = elem.getAttribute("group")
            if (group.isEmpty()) continue
            allTrusted.add(
                TrustedArtifact(
                    group = group,
                    name = elem.getAttribute("name").ifEmpty { null },
                    version = elem.getAttribute("version").ifEmpty { null },
                    fileName = elem.getAttribute("file").ifEmpty { null },
                    reason = elem.getAttribute("reason").ifEmpty { null }
                )
            )
        }

        val missingChecksumComponents = mutableListOf<VerificationComponent>()
        var totalComponents = 0
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

            totalComponents++
            if (hasMissingChecksum) {
                missingChecksumCount++
                if (missingChecksumComponents.size < request.maxMissingChecksumComponents) {
                    missingChecksumComponents.add(
                        VerificationComponent(
                            group = group,
                            name = name,
                            version = version,
                            checksumCount = checksumCount,
                            hasMissingChecksums = true
                        )
                    )
                }
            }
        }

        val maxTrusted = request.maxTrustedArtifacts.coerceAtLeast(0)
        val trustedArtifacts = allTrusted.take(maxTrusted)
        val truncated =
            missingChecksumComponents.size < missingChecksumCount ||
                trustedArtifacts.size < allTrusted.size

        val summary = buildString {
            append("Dependency Verification Metadata Audit (static XML only):\n")
            append("- Status: ${DependencyVerificationResult.STATUS_OK}\n")
            append("- Project: ${projectDir.path}\n")
            append("- Verification file: ${verificationFile.relativeTo(projectDir).path}\n")
            append("- Verify Metadata: $verifyMetadata | Verify Signatures: $verifySignatures\n")
            append("- Total Components: $totalComponents\n")
            append("- Components with Missing Checksums: $missingChecksumCount")
            if (missingChecksumComponents.size < missingChecksumCount) {
                append(" (showing ${missingChecksumComponents.size})")
            }
            append('\n')
            append("- Trusted Artifact Overrides: ${allTrusted.size}")
            if (trustedArtifacts.size < allTrusted.size) {
                append(" (showing ${trustedArtifacts.size})")
            }
            append('\n')
            if (truncated) {
                append("- Truncated: true\n")
            }
        }

        return DependencyVerificationResult(
            projectDir = projectDir.path,
            status = DependencyVerificationResult.STATUS_OK,
            verificationFileFound = true,
            verificationFilePath = verificationFile.absolutePath,
            verifyMetadata = verifyMetadata,
            verifySignatures = verifySignatures,
            totalComponents = totalComponents,
            componentsWithMissingChecksums = missingChecksumCount,
            trustedArtifactsCount = allTrusted.size,
            components = missingChecksumComponents,
            trustedArtifacts = trustedArtifacts,
            truncated = truncated,
            summary = summary
        )
    }

    private fun invalidXmlResult(
        projectDir: File,
        verificationFile: File,
        error: Throwable
    ): DependencyVerificationResult {
        val reason = error.message?.takeIf { it.isNotBlank() } ?: error.toString()
        return DependencyVerificationResult(
            projectDir = projectDir.path,
            status = DependencyVerificationResult.STATUS_INVALID_XML,
            verificationFileFound = true,
            verificationFilePath = verificationFile.absolutePath,
            failureReason = reason,
            summary = buildString {
                append("Status: ${DependencyVerificationResult.STATUS_INVALID_XML}. ")
                append("Failed to parse ${verificationFile.path}. ")
                append("Reason: ${reason.lineSequence().first()}")
            }
        )
    }

    private fun looksLikeGradleProject(projectDir: File): Boolean =
        File(projectDir, "settings.gradle.kts").isFile ||
            File(projectDir, "settings.gradle").isFile ||
            File(projectDir, "build.gradle.kts").isFile ||
            File(projectDir, "build.gradle").isFile ||
            File(projectDir, "gradle/wrapper/gradle-wrapper.properties").isFile

    private companion object {
        const val GUIDANCE_NOT_CONFIGURED =
            "Enable Gradle dependency verification, then generate metadata with " +
                "./gradlew --write-verification-metadata sha256 help " +
                "(or sha256,pgp). Re-run verify_dependency_metadata after the file exists."
    }
}
