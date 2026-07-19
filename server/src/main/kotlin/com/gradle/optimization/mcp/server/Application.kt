package com.gradle.optimization.mcp.server

import com.gradle.optimization.mcp.core.api.GradleConfig
import com.gradle.optimization.mcp.core.impl.di.coreModule
import com.gradle.optimization.mcp.features.configcache.impl.di.configurationCacheModule
import com.gradle.optimization.mcp.features.dependencyinsight.impl.di.dependencyInsightModule
import com.gradle.optimization.mcp.features.dryrun.impl.di.dryRunModule
import com.gradle.optimization.mcp.features.isolation.impl.di.projectIsolationModule
import com.gradle.optimization.mcp.features.linter.impl.di.pluginLinterModule
import com.gradle.optimization.mcp.features.verification.impl.di.dependencyVerificationModule
import com.gradle.optimization.mcp.server.di.ServerModule
import com.gradle.optimization.mcp.server.di.module
import com.gradle.optimization.mcp.server.tools.McpToolsRegistrar
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun main() = runBlocking {
    val mcpOutput = System.out
    System.setOut(System.err)

    val config = GradleConfig(
        defaultProjectDir = System.getProperty("user.dir")
    )

    val koin = startKoin {
        modules(
            module { single { config } },
            coreModule,
            dryRunModule,
            projectIsolationModule,
            dependencyInsightModule,
            configurationCacheModule,
            dependencyVerificationModule,
            pluginLinterModule,
            ServerModule().module()
        )
    }.koin

    val server = Server(
        serverInfo = Implementation(name = "gradle-optimization-mcp", version = "1.0.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true)
            )
        )
    )

    koin.getAll<McpToolsRegistrar>().forEach { it.register(server) }

    val done = CompletableDeferred<Unit>()
    val transport = StdioServerTransport(
        input = System.`in`.asSource().buffered(),
        output = mcpOutput.asSink().buffered()
    )

    server.onClose { done.complete(Unit) }
    runCatching {
        server.createSession(transport)
    }
    done.await()
}
