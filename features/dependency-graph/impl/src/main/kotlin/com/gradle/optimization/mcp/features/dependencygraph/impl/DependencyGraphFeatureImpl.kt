package com.gradle.optimization.mcp.features.dependencygraph.impl

import com.gradle.optimization.mcp.core.api.GradleConfig
import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.dependencygraph.api.DependencyGraphFeatureApi
import com.gradle.optimization.mcp.features.dependencygraph.api.DependencyNode
import com.gradle.optimization.mcp.features.dependencygraph.api.GradleDepsRequest
import com.gradle.optimization.mcp.features.dependencygraph.api.GradleDepsResult
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class DependencyGraphFeatureImpl(
    @Provided private val pool: GradleConnectionPool,
    @Provided private val config: GradleConfig
) : DependencyGraphFeatureApi {
    override fun getDependencyGraph(request: GradleDepsRequest): GradleDepsResult {
        val targetDirPath = request.projectDir ?: config.defaultProjectDir
        val targetDir = File(targetDirPath)
        require(targetDir.exists() && targetDir.isDirectory) {
            "Project directory does not exist or is not a directory: $targetDirPath"
        }

        val initScript = File.createTempFile("mcp-dependency-graph", ".gradle")
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val targetMod = request.modulePath ?: ""
        val targetCfg = request.configuration ?: ""

        try {
            initScript.writeText(
                """
                import groovy.json.JsonOutput

                allprojects {
                    afterEvaluate { p ->
                        p.tasks.register("mcpDependencyGraph") {
                            doLast {
                                def targetModPath = "$targetMod"
                                if (targetModPath != "" && p.path != targetModPath) {
                                    return
                                }

                                def targetConfigName = "$targetCfg"

                                p.configurations.each { cfg ->
                                    def isResolvable = true
                                    try {
                                        if (cfg.metaClass.respondsTo(cfg, 'isCanBeResolved')) {
                                            isResolvable = cfg.isCanBeResolved()
                                        }
                                    } catch (Throwable ignored) {
                                        isResolvable = false
                                    }

                                    if (!isResolvable) return
                                    if (targetConfigName != "" && targetConfigName != "all" && cfg.name != targetConfigName) return

                                    try {
                                        def res = cfg.incoming.resolutionResult
                                        def rootNode = res.root
                                        def directModuleIds = [] as Set

                                        rootNode.dependencies.each { dep ->
                                            if (dep.metaClass.respondsTo(dep, 'getSelected')) {
                                                def sel = dep.selected.id
                                                directModuleIds.add(sel.displayName)
                                            }
                                        }

                                        res.allDependencies.each { dep ->
                                            if (dep.metaClass.respondsTo(dep, 'getSelected')) {
                                                def requestedStr = dep.requested ? dep.requested.displayName : ""
                                                def selectedComp = dep.selected
                                                def selectedId = selectedComp.id
                                                def group = ""
                                                def name = ""
                                                def resolvedVersion = ""

                                                if (selectedId.metaClass.respondsTo(selectedId, 'getGroup')) {
                                                    group = selectedId.group
                                                    name = selectedId.module
                                                    resolvedVersion = selectedId.version
                                                } else {
                                                    def parts = selectedId.displayName.split(":")
                                                    if (parts.length >= 2) {
                                                        group = parts[0]
                                                        name = parts[1]
                                                        resolvedVersion = parts.length >= 3 ? parts[2] : ""
                                                    } else {
                                                        name = selectedId.displayName
                                                    }
                                                }

                                                def reqVersion = ""
                                                if (dep.requested.metaClass.respondsTo(dep.requested, 'getVersion')) {
                                                    reqVersion = dep.requested.version ?: ""
                                                } else {
                                                    def parts = requestedStr.split(":")
                                                    if (parts.length >= 3) reqVersion = parts[2]
                                                }
                                                if (reqVersion == "") reqVersion = resolvedVersion

                                                def isDirect = directModuleIds.contains(selectedId.displayName)
                                                def isTransitive = !isDirect
                                                def hasConflict = reqVersion != "" && resolvedVersion != "" && reqVersion != resolvedVersion
                                                def selectionReason = selectedComp.selectionReason ? selectedComp.selectionReason.toString() : "requested"

                                                def item = [
                                                    modulePath: p.path,
                                                    configuration: cfg.name,
                                                    group: group,
                                                    name: name,
                                                    requestedVersion: reqVersion,
                                                    resolvedVersion: resolvedVersion,
                                                    isDirect: isDirect,
                                                    isTransitive: isTransitive,
                                                    hasConflict: hasConflict,
                                                    selectionReason: selectionReason
                                                ]
                                                println "MCP_DEP_NODE:" + JsonOutput.toJson(item)
                                            }
                                        }
                                    } catch (Throwable t) {
                                        def errMap = [modulePath: p.path, configuration: cfg.name, error: t.message]
                                        println "MCP_DEP_ERR:" + JsonOutput.toJson(errMap)
                                    }
                                }
                            }
                        }
                    }
                }
                """.trimIndent()
            )

            pool.withConnection(targetDir) { connection ->
                val launcher = connection.newBuild()
                launcher.forTasks("mcpDependencyGraph")
                launcher.withArguments("--init-script", initScript.absolutePath, "--no-configuration-cache", "-q")
                launcher.setStandardOutput(stdout)
                launcher.setStandardError(stderr)
                launcher.run()
            }
        } finally {
            initScript.delete()
        }

        val rawOutput = stdout.toString(Charsets.UTF_8)
        val rawErr = stderr.toString(Charsets.UTF_8)

        val json = Json { ignoreUnknownKeys = true }
        val nodes = mutableListOf<DependencyNode>()
        val errors = mutableListOf<String>()

        if (rawErr.isNotBlank()) {
            errors.add(rawErr.trim())
        }

        rawOutput.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("MCP_DEP_NODE:")) {
                val jsonStr = trimmed.substringAfter("MCP_DEP_NODE:")
                runCatching {
                    val obj = json.parseToJsonElement(jsonStr).jsonObject
                    val modulePath = obj["modulePath"]?.jsonPrimitive?.content ?: ""
                    val configuration = obj["configuration"]?.jsonPrimitive?.content ?: ""
                    val group = obj["group"]?.jsonPrimitive?.content ?: ""
                    val name = obj["name"]?.jsonPrimitive?.content ?: ""
                    val requestedVersion = obj["requestedVersion"]?.jsonPrimitive?.content ?: ""
                    val resolvedVersion = obj["resolvedVersion"]?.jsonPrimitive?.content ?: ""
                    val isDirect = obj["isDirect"]?.jsonPrimitive?.booleanOrNull ?: false
                    val isTransitive = obj["isTransitive"]?.jsonPrimitive?.booleanOrNull ?: false
                    val hasConflict = obj["hasConflict"]?.jsonPrimitive?.booleanOrNull ?: false
                    val selectionReason = obj["selectionReason"]?.jsonPrimitive?.content ?: ""

                    nodes.add(
                        DependencyNode(
                            modulePath = modulePath,
                            configuration = configuration,
                            group = group,
                            name = name,
                            requestedVersion = requestedVersion,
                            resolvedVersion = resolvedVersion,
                            isDirect = isDirect,
                            isTransitive = isTransitive,
                            hasConflict = hasConflict,
                            selectionReason = selectionReason
                        )
                    )
                }
            } else if (trimmed.startsWith("MCP_DEP_ERR:")) {
                val jsonStr = trimmed.substringAfter("MCP_DEP_ERR:")
                runCatching {
                    val obj = json.parseToJsonElement(jsonStr).jsonObject
                    val mod = obj["modulePath"]?.jsonPrimitive?.content ?: ""
                    val cfgName = obj["configuration"]?.jsonPrimitive?.content ?: ""
                    val err = obj["error"]?.jsonPrimitive?.content ?: ""
                    errors.add("Error resolving $mod ($cfgName): $err")
                }
            }
        }

        val filteredNodes = nodes.filter { node ->
            val transitiveFilter = request.includeTransitive || node.isDirect
            val conflictFilter = !request.onlyConflicts || node.hasConflict
            transitiveFilter && conflictFilter
        }

        val modulesAnalyzed = filteredNodes.map { it.modulePath }.distinct()
        val conflictCount = filteredNodes.count { it.hasConflict }

        return GradleDepsResult(
            projectDir = targetDirPath,
            totalDependencies = filteredNodes.size,
            conflictCount = conflictCount,
            modulesAnalyzed = modulesAnalyzed,
            dependencies = filteredNodes,
            errors = errors
        )
    }
}
