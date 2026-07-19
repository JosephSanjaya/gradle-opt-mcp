package com.gradle.optimization.mcp.core.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.koin.core.annotation.Single
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Single
class GradleConnectionPoolImpl : GradleConnectionPool, AutoCloseable {
    private val connections = ConcurrentHashMap<File, ProjectConnection>()

    override fun <T> withConnection(projectDir: File, action: (ProjectConnection) -> T): T {
        val canonicalDir = projectDir.canonicalFile
        val connection = connections.computeIfAbsent(canonicalDir) { dir ->
            GradleConnector.newConnector()
                .forProjectDirectory(dir)
                .connect()
        }
        return action(connection)
    }

    override fun close() {
        connections.values.forEach { connection ->
            runCatching { connection.close() }
        }
        connections.clear()
    }
}
