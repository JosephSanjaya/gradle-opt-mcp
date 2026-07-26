package com.gradle.optimization.mcp.features.dependencygraph.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.dependencygraph.api.DependencyGraphFeatureApi
import com.gradle.optimization.mcp.features.dependencygraph.api.DependencyNode
import com.gradle.optimization.mcp.features.dependencygraph.api.GradleDepsRequest
import com.gradle.optimization.mcp.features.dependencygraph.api.GradleDepsResult
import com.gradle.optimization.mcp.features.dependencygraph.api.SelectionReasonInfo
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class DependencyGraphFeatureImpl(
    @Provided private val pool: GradleConnectionPool
) : DependencyGraphFeatureApi {
    override fun getDependencyGraph(request: GradleDepsRequest): GradleDepsResult {
        val targetDirPath = request.projectDir.trim()
        require(targetDirPath.isNotEmpty()) { "projectDir is required" }
        val targetDir = File(targetDirPath)
        require(targetDir.exists() && targetDir.isDirectory) {
            "Project directory does not exist or is not a directory: $targetDirPath"
        }

        val initScript = File.createTempFile("mcp-dependency-graph", ".gradle")
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val targetMod = request.modulePath.orEmpty()
        val targetCfg = request.configuration.orEmpty()
        val allowlistJson = GradleDepsRequest.DEFAULT_CONFIGURATION_ALLOWLIST
            .joinToString(prefix = "[", postfix = "]", separator = ", ") { "\"$it\"" }

        try {
            initScript.writeText(buildInitScript(targetMod, targetCfg, allowlistJson))

            pool.withConnection(targetDir) { connection ->
                val launcher = connection.newBuild()
                launcher.forTasks("mcpDependencyGraphRoot")
                launcher.withArguments("--init-script", initScript.absolutePath, "--no-configuration-cache", "-q")
                launcher.setStandardOutput(stdout)
                launcher.setStandardError(stderr)
                launcher.run()
            }
        } finally {
            initScript.delete()
        }

        return parseOutput(
            projectDir = targetDirPath,
            rawOutput = stdout.toString(Charsets.UTF_8),
            rawErr = stderr.toString(Charsets.UTF_8),
            request = request
        )
    }

    internal fun parseOutput(
        projectDir: String,
        rawOutput: String,
        rawErr: String,
        request: GradleDepsRequest
    ): GradleDepsResult {
        val json = Json { ignoreUnknownKeys = true }
        val nodes = mutableListOf<DependencyNode>()
        val errors = mutableListOf<String>()
        val knownModules = mutableListOf<String>()
        val scannedConfigs = linkedSetOf<String>()

        if (rawErr.isNotBlank()) {
            errors.add(rawErr.trim())
        }

        rawOutput.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("MCP_DEP_PROJECTS:") -> {
                    val jsonStr = trimmed.substringAfter("MCP_DEP_PROJECTS:")
                    runCatching {
                        json.parseToJsonElement(jsonStr).jsonArray.forEach { el ->
                            knownModules.add(el.jsonPrimitive.content)
                        }
                    }
                }
                trimmed.startsWith("MCP_DEP_NODE:") -> {
                    val jsonStr = trimmed.substringAfter("MCP_DEP_NODE:")
                    runCatching {
                        val obj = json.parseToJsonElement(jsonStr).jsonObject
                        val reasonObj = obj["selectionReason"]?.jsonObject
                        val descriptions = reasonObj?.get("descriptions")?.jsonArray
                            ?.map { it.jsonPrimitive.content }
                            .orEmpty()
                        val reason = SelectionReasonInfo(
                            descriptions = descriptions,
                            conflictResolution = reasonObj?.get("conflictResolution")
                                ?.jsonPrimitive?.booleanOrNull ?: false,
                            constrained = reasonObj?.get("constrained")
                                ?.jsonPrimitive?.booleanOrNull ?: false,
                            forced = reasonObj?.get("forced")
                                ?.jsonPrimitive?.booleanOrNull ?: false,
                            expected = reasonObj?.get("expected")
                                ?.jsonPrimitive?.booleanOrNull ?: false
                        )
                        val configuration = obj["configuration"]?.jsonPrimitive?.content.orEmpty()
                        scannedConfigs.add(configuration)
                        nodes.add(
                            DependencyNode(
                                modulePath = obj["modulePath"]?.jsonPrimitive?.content.orEmpty(),
                                configuration = configuration,
                                group = obj["group"]?.jsonPrimitive?.content.orEmpty(),
                                name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                                requestedVersion = obj["requestedVersion"]?.jsonPrimitive?.content.orEmpty(),
                                resolvedVersion = obj["resolvedVersion"]?.jsonPrimitive?.content.orEmpty(),
                                isDirect = obj["isDirect"]?.jsonPrimitive?.booleanOrNull ?: false,
                                isTransitive = obj["isTransitive"]?.jsonPrimitive?.booleanOrNull ?: false,
                                hasConflict = obj["hasConflict"]?.jsonPrimitive?.booleanOrNull ?: false,
                                selectionReason = reason,
                                componentKind = obj["componentKind"]?.jsonPrimitive?.content ?: "module"
                            )
                        )
                    }
                }
                trimmed.startsWith("MCP_DEP_ERR:") -> {
                    val jsonStr = trimmed.substringAfter("MCP_DEP_ERR:")
                    runCatching {
                        val obj = json.parseToJsonElement(jsonStr).jsonObject
                        val mod = obj["modulePath"]?.jsonPrimitive?.content.orEmpty()
                        val cfgName = obj["configuration"]?.jsonPrimitive?.content.orEmpty()
                        val err = obj["error"]?.jsonPrimitive?.content.orEmpty()
                        if (mod.isNotEmpty() || cfgName.isNotEmpty()) {
                            errors.add("Error resolving $mod ($cfgName): $err")
                        } else {
                            errors.add(err)
                        }
                    }
                }
            }
        }

        val modulePath = request.modulePath
        if (!modulePath.isNullOrBlank()) {
            require(knownModules.isEmpty() || knownModules.contains(modulePath)) {
                "Unknown modulePath: '$modulePath'. Known modules: ${knownModules.sorted().joinToString(", ")}"
            }
            // ponytail: if Gradle emitted no project list (older path), still reject empty module hits
            if (knownModules.isEmpty() && nodes.none { it.modulePath == modulePath }) {
                error("Unknown modulePath: '$modulePath' (no matching module produced dependency data)")
            }
        }

        return DependencyGraphSummarizer.summarize(
            projectDir = projectDir,
            nodes = nodes,
            configurationsScanned = scannedConfigs.toList().sorted(),
            request = request,
            errors = errors
        )
    }

    private fun buildInitScript(targetMod: String, targetCfg: String, allowlistJson: String): String {
        // language=Groovy
        return """
            import groovy.json.JsonOutput

            def targetModPath = "$targetMod"
            def targetConfigName = "$targetCfg"
            def defaultAllowlist = $allowlistJson as Set

            rootProject {
                tasks.register("mcpDependencyGraphRoot") {
                    group = "verification"
                    // Deferred so leaf tasks exist when the task graph is built.
                    dependsOn {
                        def allPaths = rootProject.allprojects.collect { it.path }
                        if (targetModPath == "") {
                            return rootProject.allprojects.collect { p ->
                                p.tasks.named("mcpDependencyGraph")
                            }
                        }
                        if (allPaths.contains(targetModPath)) {
                            return [rootProject.project(targetModPath).tasks.named("mcpDependencyGraph")]
                        }
                        return []
                    }
                    doFirst {
                        def allPaths = rootProject.allprojects.collect { it.path }
                        println "MCP_DEP_PROJECTS:" + JsonOutput.toJson(allPaths)
                        if (targetModPath != "" && !allPaths.contains(targetModPath)) {
                            def errMap = [
                                error: "Unknown modulePath: '" + targetModPath +
                                    "'. Known modules: " + allPaths.sort().join(", ")
                            ]
                            println "MCP_DEP_ERR:" + JsonOutput.toJson(errMap)
                        }
                    }
                }
            }

            allprojects { proj ->
                afterEvaluate { p ->
                    p.tasks.register("mcpDependencyGraph") {
                        outputs.upToDateWhen { false }
                        def selectedConfigs = p.configurations.findAll { cfg ->
                            def isResolvable = true
                            try {
                                if (cfg.metaClass.respondsTo(cfg, 'isCanBeResolved')) {
                                    isResolvable = cfg.isCanBeResolved()
                                }
                            } catch (Throwable ignored) {
                                isResolvable = false
                            }
                            if (!isResolvable) return false
                            if (targetConfigName == "all") return true
                            if (targetConfigName != "") return cfg.name == targetConfigName
                            return defaultAllowlist.contains(cfg.name)
                        }
                        // Acquire Gradle's exclusive resolution lock for each config.
                        selectedConfigs.each { cfg ->
                            inputs.files(cfg).withPropertyName("mcpDeps_" + cfg.name).optional(true)
                        }
                        doLast {
                            if (targetModPath != "" && p.path != targetModPath) {
                                return
                            }
                            selectedConfigs.each { cfg ->
                                try {
                                    def res = cfg.incoming.resolutionResult
                                    def rootNode = res.root
                                    def directModuleIds = [] as Set

                                    rootNode.dependencies.each { dep ->
                                        if (dep.metaClass.respondsTo(dep, 'getSelected')) {
                                            directModuleIds.add(dep.selected.id.displayName)
                                        }
                                    }

                                    res.allDependencies.each { dep ->
                                        if (!dep.metaClass.respondsTo(dep, 'getSelected')) return

                                        def requestedStr = dep.requested ? dep.requested.displayName : ""
                                        def selectedComp = dep.selected
                                        def selectedId = selectedComp.id
                                        def group = ""
                                        def name = ""
                                        def resolvedVersion = ""
                                        def componentKind = "module"

                                        if (selectedId.metaClass.respondsTo(selectedId, 'getProjectPath')) {
                                            componentKind = "project"
                                            group = "project"
                                            name = selectedId.projectPath
                                            resolvedVersion = ""
                                        } else if (selectedId.class.name.contains("ProjectComponent")) {
                                            componentKind = "project"
                                            group = "project"
                                            name = selectedId.displayName.replaceFirst(/^project\s+/, "")
                                            resolvedVersion = ""
                                        } else if (selectedId.metaClass.respondsTo(selectedId, 'getGroup')) {
                                            group = selectedId.group
                                            name = selectedId.module
                                            resolvedVersion = selectedId.version
                                        } else {
                                            def parts = selectedId.displayName.split(":")
                                            if (parts.length >= 3 && !selectedId.displayName.startsWith("project ")) {
                                                group = parts[0]
                                                name = parts[1]
                                                resolvedVersion = parts[2]
                                            } else {
                                                name = selectedId.displayName
                                            }
                                        }

                                        def reqVersion = ""
                                        if (dep.requested.metaClass.respondsTo(dep.requested, 'getVersion')) {
                                            reqVersion = dep.requested.version ?: ""
                                        } else if (dep.requested.metaClass.respondsTo(dep.requested, 'getProjectPath')) {
                                            reqVersion = ""
                                        } else {
                                            def parts = requestedStr.split(":")
                                            if (parts.length >= 3 && !requestedStr.startsWith("project ")) {
                                                reqVersion = parts[2]
                                            }
                                        }
                                        if (reqVersion == "" && componentKind != "project") {
                                            reqVersion = resolvedVersion
                                        }

                                        def isDirect = directModuleIds.contains(selectedId.displayName)
                                        def hasConflict = componentKind != "project" &&
                                            reqVersion != "" && resolvedVersion != "" && reqVersion != resolvedVersion

                                        def reason = selectedComp.selectionReason
                                        def descriptions = []
                                        try {
                                            reason.descriptions.each { d ->
                                                def text = d.metaClass.respondsTo(d, 'getDescription') ?
                                                    d.description : d.toString()
                                                if (text) descriptions.add(text.toString())
                                            }
                                        } catch (Throwable ignored) {}

                                        def selectionReason = [
                                            descriptions: descriptions.take(5),
                                            conflictResolution: reason.conflictResolution,
                                            constrained: reason.constrained,
                                            forced: reason.forced,
                                            expected: reason.expected
                                        ]

                                        def item = [
                                            modulePath: p.path,
                                            configuration: cfg.name,
                                            group: group,
                                            name: name,
                                            requestedVersion: reqVersion,
                                            resolvedVersion: resolvedVersion,
                                            isDirect: isDirect,
                                            isTransitive: !isDirect,
                                            hasConflict: hasConflict,
                                            selectionReason: selectionReason,
                                            componentKind: componentKind
                                        ]
                                        println "MCP_DEP_NODE:" + JsonOutput.toJson(item)
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
    }
}
