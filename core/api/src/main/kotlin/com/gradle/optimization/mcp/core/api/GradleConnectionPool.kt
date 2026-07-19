package com.gradle.optimization.mcp.core.api

import org.gradle.tooling.ProjectConnection
import java.io.File

interface GradleConnectionPool {
    fun <T> withConnection(projectDir: File, action: (ProjectConnection) -> T): T
}
