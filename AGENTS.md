# Imperium Agent Guidelines

This document provides essential information for agentic coding agents working on the Imperium codebase.

## Project Overview

Imperium is the core of the Xpdustry network, a collection of services and plugins for Mindustry and Discord. It is a Kotlin-based multi-module Gradle project.

- **Modules**:
  - `imperium-common`: Shared logic, database models, and utilities.
  - `imperium-mindustry`: Mindustry plugin implementation.
  - `imperium-discord`: Discord bot implementation.
  - `imperium-backend-java`: Java-based backend services.
  - `imperium-build-logic`: Gradle convention plugins.

## Build and Development Commands

- **Build all**: `./gradlew build` (compiles, lints, and runs tests)
- **Compile only**: `./gradlew shadowJar`
- **Run Tests**: `./gradlew test`
- **Run Single Test**: `./gradlew :<module>:test --tests "<fully.qualified.ClassName>"`
  - *Example*: `./gradlew :imperium-common:test --tests "com.xpdustry.imperium.common.security.PunishmentManagerTest"`
- **Format Code**: `./gradlew spotlessApply` (**Required before PRs**)
- **Check Formatting**: `./gradlew spotlessCheck`
- **Run Mindustry Server**: `./gradlew :imperium-mindustry:runMindustryServer`
- **Run Mindustry Client**: `./gradlew :imperium-mindustry:runMindustryClient`
- **Run Discord Bot**: `./gradlew :imperium-discord:runShadow`

## Tech Stack

- **Language**: Kotlin 2.0+ (JVM 17/25)
- **Database**: Exposed (SQL framework) with MariaDB/H2.
- **Serialization**: `kotlinx.serialization` (JSON/YAML).
- **Testing**: JUnit 5, Testcontainers.
- **Logging**: SLF4J / Logback.
- **Dependency Injection**: Custom lightweight implementation (see `com.xpdustry.imperium.common.inject`).

## Coding Guidelines

### 1. Formatting & Style
- **Standard**: Follow the `kotlinlang` style via `ktfmt`.
- **Max Line Length**: 120 characters.
- **Trailing Commas**: Use trailing commas in multi-line parameter lists, arrays, etc.
- **Indentation**: 4 spaces.

### 2. Naming Conventions
- **Interfaces**: PascalCase, e.g., `PunishmentManager`.
- **Implementations**: Prefix with `Simple` if it's the default implementation, e.g., `SimplePunishmentManager`.
- **Exposed Tables**: Suffix with `Table`, e.g., `PunishmentTable`.
- **Functions/Variables**: `camelCase`.
- **Constants**: `UPPER_SNAKE_CASE` for top-level or companion object constants.

### 3. Imports
- Alphabetical order.
- **No Wildcards**: Never use `import .*`.
- Grouping: Standard library, then third-party libraries, then project imports.

### 4. Architecture & Patterns
- **Interfaces First**: Define service logic in interfaces within `imperium-common` or module-specific packages.
- **Coroutines**: Use `suspend` functions for asynchronous or I/O-bound operations.
- **Exposed Transactions**: Use `SQLProvider.newSuspendTransaction { ... }` for database operations.
- **Immutability**: Prefer `val` over `var`. Use `data class` for models.
- **Sum Types**: Use `sealed interface` or `sealed class` for restricted class hierarchies (e.g., `Identity`, `Metric`).

### 5. Error Handling
- **Domain Results**: Prefer returning enums or sealed classes for expected failure modes (e.g., `PardonResult { SUCCESS, NOT_FOUND, ... }`).
- **Exceptions**: Use exceptions only for truly exceptional/unexpected cases or fatal errors.

### 6. Dependency Management
- All dependencies are managed in `gradle/libs.versions.toml`. Do not hardcode versions in `build.gradle.kts` files.

## Module Breakdown

### `imperium-common`
Contains the core business logic and models used across all platforms.
- `com.xpdustry.imperium.common.security`: Authentication, punishments, identities.
- `com.xpdustry.imperium.common.user`: User profiles and management.
- `com.xpdustry.imperium.common.database`: SQL utilities and providers.
- `com.xpdustry.imperium.common.message`: Global messaging/broadcasting system.
- `com.xpdustry.imperium.common.inject`: Lightweight DI container.

### `imperium-mindustry`
Implementation of the Mindustry plugin.
- `ImperiumPlugin`: Main entry point.
- Listeners and Commands for in-game features.

### `imperium-discord`
Implementation of the Discord bot.
- Integration with JDA.
- Bridge between Discord and Mindustry.

## Dependency Injection (DI)

Imperium uses a custom lightweight DI system in `com.xpdustry.imperium.common.inject`.
- **Registering**: `instances.register(MyService::class, SimpleMyService(...))`
- **Injecting**: `val service = instances.get<MyService>()`
- Services often implement `ImperiumApplication.Listener` to participate in the application lifecycle (`onImperiumInit`, `onImperiumExit`).

## Database Operations

Use Exposed with the `SQLProvider`.
```kotlin
// In a service
override suspend fun findUser(id: Int): User? = 
    provider.newSuspendTransaction {
        UserTable.selectAll().where { UserTable.id eq id }.firstOrNull()?.toUser()
    }
```

## Messaging System

Broadcasting messages across the network (e.g., chat, punishments):
```kotlin
messenger.broadcast(MyMessage(...), local = false)
```

## Adding a New Service

1. Define an interface in `imperium-common`.
2. Create a `Simple` implementation.
3. If database access is needed, define a `Table` object.
4. Register the service in `CommonModule.kt` or module-specific initialization.
5. Add unit tests in `src/test/kotlin`.

## File Header

All Kotlin source files must include the standard GPL-3.0 license header found in `LICENSE_HEADER.md`.

```kotlin
/*
 * Imperium, the software collection powering the Chaotic Neutral network.
 * Copyright (C) 2024  Xpdustry
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * ...
 */
```

## Testing Guidelines

- Place tests in `src/test/kotlin`.
- Use JUnit 5 `@Test` annotation.
- For database-related tests, use `Testcontainers` (MariaDB) if necessary, or H2 for lightweight unit tests.
- Use `kotlinx-coroutines-test` for testing suspend functions.
