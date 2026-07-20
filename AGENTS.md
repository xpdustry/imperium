# AGENTS.md

## Introduction

This is Imperium, the software powering the Chaotic Neutral Mindustry server network.
It is written in Kotlin, the build tool is Gradle and there are 3 subprojects.
- `imperium-common`: Common types and classes shared by the other platforms
- `imperium-[discord|backend]`: The backend server, responsible for the discord integration and REST API.
- `imperium-mindustry`: The plugin running in Mindustry,
  provides utilities and exclusive functionalities to our players.

## Instructions

- Compile with subproject:jar, Test with subproject:test, Lint with subproject:spotlessApply,
  You SHOULD only use subproject:build when you are done.
