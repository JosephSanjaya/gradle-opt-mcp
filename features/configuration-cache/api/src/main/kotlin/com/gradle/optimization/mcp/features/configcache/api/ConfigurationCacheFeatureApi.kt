package com.gradle.optimization.mcp.features.configcache.api

interface ConfigurationCacheFeatureApi {
    fun auditConfigurationCacheInputs(request: ConfigCacheAuditRequest): ConfigCacheAuditResult
}
