package com.gradle.optimization.mcp.features.verification.impl

import com.gradle.optimization.mcp.features.verification.api.DependencyVerificationRequest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DependencyVerificationFeatureImplTest {
    @Test
    fun `returns false when verification file does not exist`() {
        val tempDir = createTempDirectory().toFile()
        try {
            val impl = DependencyVerificationFeatureImpl()
            val result = impl.verifyDependencyMetadata(DependencyVerificationRequest(tempDir.absolutePath))

            assertFalse(result.verificationFileFound)
            assertEquals(0, result.totalComponents)
            assertTrue(result.summary.contains("No gradle/verification-metadata.xml found"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `parses valid verification metadata XML file`() {
        val tempDir = createTempDirectory().toFile()
        try {
            val gradleDir = File(tempDir, "gradle").apply { mkdirs() }
            val verificationXml = File(gradleDir, "verification-metadata.xml")
            verificationXml.writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <to-verify xmlns="https://schema.gradle.org/dependency-verification" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                   <configuration>
                      <verify-metadata>true</verify-metadata>
                      <verify-signatures>false</verify-signatures>
                   </configuration>
                   <configuration-attributes>
                      <trusted-artifact group="com.google.guava" name="guava" version="30.1-jre" reason="Internal override"/>
                   </configuration-attributes>
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
                </to-verify>
                """.trimIndent()
            )

            val impl = DependencyVerificationFeatureImpl()
            val result = impl.verifyDependencyMetadata(DependencyVerificationRequest(tempDir.absolutePath))

            assertTrue(result.verificationFileFound)
            assertTrue(result.verifyMetadata)
            assertFalse(result.verifySignatures)
            assertEquals(2, result.totalComponents)
            assertEquals(1, result.componentsWithMissingChecksums)
            assertEquals(1, result.trustedArtifactsCount)
            assertEquals("com.google.guava", result.trustedArtifacts[0].group)
            assertEquals("guava", result.trustedArtifacts[0].name)
            assertEquals("Internal override", result.trustedArtifacts[0].reason)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
