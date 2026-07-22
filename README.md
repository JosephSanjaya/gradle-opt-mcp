# Gradle Optimization MCP Server

[![CI](https://github.com/JosephSanjaya/gradle-opt-mcp/actions/workflows/ci.yml/badge.svg)](https://github.com/JosephSanjaya/gradle-opt-mcp/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/JosephSanjaya/gradle-opt-mcp?include_prereleases)](https://github.com/JosephSanjaya/gradle-opt-mcp/releases)

> A Model Context Protocol (MCP) server that gives Claude direct, structured access to your Gradle build — dependency graphs, build scans, cache health, test results, and more — without you pasting logs by hand.

## Table of Contents
- [About](#about)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Install](#install)
- [Configuration](#configuration)
- [Usage](#usage)
- [Development](#development)
- [Releases](#releases)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

## About
Diagnosing a slow or broken Gradle build usually means running a command, copying its output, and pasting it into a chat. This server removes that step: it speaks MCP over stdio, so Claude can call Gradle tools directly — inspect dependency insight, run a dry-run analysis, profile cache invalidation, or pull a test summary — and reason over structured results instead of raw console text.

## Features
| Tool | What it does |
|---|---|
| `analyze_build_dry_run` | Analyzes a `--dry-run` task graph without executing the build |
| `check_project_isolation_violations` | Flags cross-project state access that breaks Project Isolation |
| `get_dependency_insight` | Explains why a dependency resolved to a given version |
| `audit_configuration_cache_inputs` | Audits what invalidates the configuration cache |
| `verify_dependency_metadata` | Verifies dependency metadata/signatures |
| `lint_gradle_plugins` | Lints applied Gradle plugins for known issues |
| `analyze_build_scan` | Analyzes a Gradle Build Scan |
| `inspect_declarative_schemas` | Inspects declarative Gradle DSL schemas |
| `profile_cache_invalidation_timeline` | Profiles build/configuration cache invalidation over time |
| `analyze_parallelization_bottlenecks` | Finds tasks blocking parallel execution |
| `gradle_health` | Reports project health (modules, versions, structure) |
| `gradle_run` / `gradle_run_log` | Runs a Gradle task and retrieves its log |
| `gradle_test_summary` | Summarizes test results for a run |
| `gradle_deps` | Renders the resolved dependency graph |

## Tech Stack
- **Language:** Kotlin 2.4.10 (Multiplatform, JVM target)
- **Build:** Gradle 9.6.1
- **Runtime:** Ktor server core/Netty, Koin DI (compiler-plugin annotations)
- **Protocol:** [Model Context Protocol](https://modelcontextprotocol.io) Kotlin SDK, stdio transport
- **CI/CD:** GitHub Actions (trunk-based CI + tag-triggered releases), [git-cliff](https://git-cliff.org) for release notes

## Prerequisites
| Requirement | Minimum Version | Notes |
|---|---|---|
| JDK | 21 | Required to run the fat jar and to build the project |
| Gradle | 9.6.1 | Only needed if building from source. The wrapper (`./gradlew`) handles this |
| An MCP-capable client | — | e.g. Claude Desktop or Claude Code |

## Install

**Option A — download a release (recommended):**
```bash
gh release download --repo JosephSanjaya/gradle-opt-mcp --pattern '*.jar'
java -jar gradle-opt-mcp-*.jar
```
The process blocks waiting for stdio input — that's expected. It only responds when an MCP client connects.

**Option B — build from source:**
```bash
git clone https://github.com/JosephSanjaya/gradle-opt-mcp.git
cd gradle-opt-mcp
./gradlew :server:fatJar
java -jar server/build/libs/*-all.jar
```

## Configuration
The server reads these environment variables at startup:

| Variable | Default | Description |
|---|---|---|
| `GRADLE_PROJECT_DIR` | current working directory | Absolute path to the Gradle project the tools should analyze |
| `GRADLE_VARIANT` | none | Build variant/flavor passed to tools that need one (e.g. Android product flavors) |

Set these in your MCP client config, not as global exports — each project you analyze needs its own value.

## Usage
Add the server to your MCP client config, pointing `args` at the downloaded/built jar and `GRADLE_PROJECT_DIR` at the project you want Claude to inspect:

```json
{
  "mcpServers": {
    "gradle-optimization": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/gradle-opt-mcp-1.0.0.jar"],
      "env": {
        "GRADLE_PROJECT_DIR": "/absolute/path/to/your/gradle/project"
      }
    }
  }
}
```

Restart your MCP client, then ask Claude something like "check this project's Gradle health" or "why did `com.google.guava:guava` resolve to this version?" — Claude calls the matching tool and reasons over the structured result.

## Development
| Intent | Command |
|---|---|
| Compile | `./gradlew compileKotlin` |
| Build server distribution | `./gradlew :server:installDist` |
| Build fat jar | `./gradlew :server:fatJar` |
| Lint & auto-format | `./gradlew detekt` |
| Run all tests | `./gradlew test` |

## Releases
This project is trunk-based: `main` is always releasable, there are no long-lived branches. Every push to `main` and every PR runs CI (`detekt` + `test`, plus a Conventional Commits check on PRs).

To cut a release, push a tag matching `vX.Y.Z` (optionally `-alpha`/`-beta`/`-rc`) from a commit on `main`. The [release workflow](./.github/workflows/release.yml) then:
1. Verifies the tag's commit is reachable from `origin/main`
2. Builds the fat jar with the tag as its version
3. Generates a SHA-256 checksum for the jar
4. Generates release notes from commit history with git-cliff
5. Publishes a GitHub Release with the jar, checksum, and notes attached

## Troubleshooting
**`java.lang.UnsupportedClassVersionError`**
Cause: running the jar with a JDK older than 21.
Fix: install JDK 21+ (e.g. Temurin) and confirm with `java -version`.

**Claude/your MCP client shows the server as unavailable or with no tools**
Cause: the `args` path in your MCP config doesn't point at a real jar file, or `GRADLE_PROJECT_DIR` doesn't exist.
Fix: run `java -jar <path>` manually from a terminal — if it starts without printing an error and just sits idle, the jar is fine and the issue is the client config path.

**A Gradle-backed tool call fails or times out**
Cause: `GRADLE_PROJECT_DIR` points at a directory with no Gradle wrapper (no `gradlew`), or the target project itself fails to configure.
Fix: point `GRADLE_PROJECT_DIR` at the project root containing `gradlew`, and confirm that project builds standalone first.

## Contributing
1. Fork the repo and branch off `main`
2. Make focused, independently-compiling commits — every commit must pass `./gradlew detekt test`
3. Follow [Conventional Commits](https://www.conventionalcommits.org): `feat`, `fix`, `chore`, `refactor`, `docs`, `test`, `perf`, `build`, `ci`
4. Open a PR into `main` — CI enforces the commit format and runs the full build

## License
No license file is published in this repository yet. Until one is added, all rights are reserved by the author.
