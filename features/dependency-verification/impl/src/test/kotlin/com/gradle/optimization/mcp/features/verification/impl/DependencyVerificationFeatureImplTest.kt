package com.gradle.optimization.mcp.features.verification.impl

import com.gradle.optimization.mcp.features.verification.api.DependencyVerificationRequest
import com.gradle.optimization.mcp.features.verification.api.DependencyVerificationResult
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DependencyVerificationFeatureImplTest {
    @Test
    fun `returns VERIFICATION_NOT_CONFIGURED when file missing`() {
        val tempDir = createTempDirectory().toFile()
        try {
            File(tempDir, "settings.gradle.kts").writeText("rootProject.name = \"demo\"")
            val impl = DependencyVerificationFeatureImpl()
            val result = impl.verifyDependencyMetadata(DependencyVerificationRequest(tempDir.absolutePath))

            assertEquals(DependencyVerificationResult.STATUS_VERIFICATION_NOT_CONFIGURED, result.status)
            assertFalse(result.verificationFileFound)
            assertEquals(0, result.totalComponents)
            assertNotNull(result.guidance)
            assertTrue(result.guidance!!.contains("--write-verification-metadata"))
            assertTrue(result.summary.contains(DependencyVerificationResult.STATUS_VERIFICATION_NOT_CONFIGURED))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `rejects non-Gradle directory`() {
        val tempDir = createTempDirectory().toFile()
        try {
            val impl = DependencyVerificationFeatureImpl()
            val result = impl.verifyDependencyMetadata(DependencyVerificationRequest(tempDir.absolutePath))

            assertEquals(DependencyVerificationResult.STATUS_NOT_A_GRADLE_PROJECT, result.status)
            assertFalse(result.verificationFileFound)
            assertNotNull(result.failureReason)
            assertTrue(result.failureReason!!.contains("Not a Gradle project"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `returns structured failureReason for invalid XML`() {
        val tempDir = createTempDirectory().toFile()
        try {
            File(tempDir, "settings.gradle.kts").writeText("rootProject.name = \"demo\"")
            val gradleDir = File(tempDir, "gradle").apply { mkdirs() }
            File(gradleDir, "verification-metadata.xml").writeText("<not-valid")

            val impl = DependencyVerificationFeatureImpl()
            val result = impl.verifyDependencyMetadata(DependencyVerificationRequest(tempDir.absolutePath))

            assertEquals(DependencyVerificationResult.STATUS_INVALID_XML, result.status)
            assertTrue(result.verificationFileFound)
            assertNotNull(result.failureReason)
            assertTrue(result.summary.contains(DependencyVerificationResult.STATUS_INVALID_XML))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `parses valid verification metadata XML file`() {
        val tempDir = createTempDirectory().toFile()
        try {
            File(tempDir, "settings.gradle.kts").writeText("rootProject.name = \"demo\"")
            writeVerificationXml(
                tempDir,
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <verification-metadata xmlns="https://schema.gradle.org/dependency-verification"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                   <configuration>
                      <verify-metadata>true</verify-metadata>
                      <verify-signatures>false</verify-signatures>
                   </configuration>
                   <trusted-artifacts>
                      <trusted-artifact group="com.google.guava" name="guava" version="30.1-jre" reason="Internal override"/>
                   </trusted-artifacts>
                   <components>
                      <component group="org.jetbrains.kotlin" name="kotlin-stdlib" version="1.9.0">
                         <artifact name="kotlin-stdlib-1.9.0.jar">
                            <sha256 value="1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"/>
                         </artifact>
                      </component>
                      <component group="com.example" name="unverified-lib" version="1.0.0">
                         <artifact name="unverified-lib-1.0.0.jar"/>
                      </component>
                   </components>
                </verification-metadata>
                """.trimIndent()
            )

            val impl = DependencyVerificationFeatureImpl()
            val result = impl.verifyDependencyMetadata(DependencyVerificationRequest(tempDir.absolutePath))

            assertEquals(DependencyVerificationResult.STATUS_OK, result.status)
            assertTrue(result.verificationFileFound)
            assertTrue(result.verifyMetadata)
            assertFalse(result.verifySignatures)
            assertEquals(2, result.totalComponents)
            assertEquals(1, result.componentsWithMissingChecksums)
            assertEquals(1, result.components.size)
            assertEquals("com.example", result.components[0].group)
            assertEquals(1, result.trustedArtifactsCount)
            assertEquals("com.google.guava", result.trustedArtifacts[0].group)
            assertEquals("guava", result.trustedArtifacts[0].name)
            assertEquals("Internal override", result.trustedArtifacts[0].reason)
            assertFalse(result.truncated)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `caps missing checksum components and trusted artifacts with truncated`() {
        val tempDir = createTempDirectory().toFile()
        try {
            File(tempDir, "settings.gradle.kts").writeText("rootProject.name = \"demo\"")
            val xml = buildString {
                appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
                appendLine("""<verification-metadata xmlns="https://schema.gradle.org/dependency-verification">""")
                appendLine("<configuration><verify-metadata>true</verify-metadata>")
                appendLine("<verify-signatures>false</verify-signatures></configuration>")
                appendLine("<trusted-artifacts>")
                repeat(5) { i ->
                    appendLine("""<trusted-artifact group="com.trusted" name="t-$i" version="1.0"/>""")
                }
                appendLine("</trusted-artifacts>")
                appendLine("<components>")
                repeat(10) { i ->
                    appendLine("""<component group="com.example" name="lib-$i" version="1.0.0">""")
                    appendLine("""<artifact name="lib-$i-1.0.0.jar"/></component>""")
                }
                appendLine("</components>")
                appendLine("</verification-metadata>")
            }
            writeVerificationXml(tempDir, xml)

            val impl = DependencyVerificationFeatureImpl()
            val result = impl.verifyDependencyMetadata(
                DependencyVerificationRequest(
                    projectDir = tempDir.absolutePath,
                    maxMissingChecksumComponents = 3,
                    maxTrustedArtifacts = 2
                )
            )

            assertEquals(DependencyVerificationResult.STATUS_OK, result.status)
            assertEquals(10, result.totalComponents)
            assertEquals(10, result.componentsWithMissingChecksums)
            assertEquals(3, result.components.size)
            assertEquals(5, result.trustedArtifactsCount)
            assertEquals(2, result.trustedArtifacts.size)
            assertTrue(result.truncated)
            assertTrue(result.summary.contains("Truncated: true"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun writeVerificationXml(projectDir: File, xml: String) {
        val gradleDir = File(projectDir, "gradle").apply { mkdirs() }
        File(gradleDir, "verification-metadata.xml").writeText(xml)
    }
}
