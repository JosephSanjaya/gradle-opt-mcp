package com.gradle.optimization.mcp.server.tools

import io.modelcontextprotocol.kotlin.sdk.server.Server

interface McpToolsRegistrar {
    fun register(server: Server)
}
