package com.gradle.optimization.mcp.features.declarative.api

interface DeclarativeSchemaFeatureApi {
    fun inspectDeclarativeSchemas(request: DeclarativeSchemaRequest): DeclarativeSchemaResult
}
