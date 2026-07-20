package com.gradle.optimization.mcp.features.cacheprofiler.api

interface CacheProfilerFeatureApi {
    fun profileCacheInvalidationTimeline(request: CacheProfileRequest): CacheProfileResult
}
