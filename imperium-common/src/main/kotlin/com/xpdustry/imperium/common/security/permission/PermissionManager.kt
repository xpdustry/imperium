/*
 * Imperium, the software collection powering the Xpdustry network.
 * Copyright (C) 2023  Xpdustry
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.xpdustry.imperium.common.security.permission

import com.google.common.cache.CacheBuilder
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.message.Message
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.snowflake.Snowflake
import java.time.Duration
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.upsert

interface PermissionManager {

    suspend fun getPermission(
        identity: Identity.Mindustry,
        permission: Permission
    ): Permission.Scope

    suspend fun getPermission(account: Snowflake, permission: Permission): Permission.Scope

    suspend fun setPermission(
        account: Snowflake,
        permission: Permission,
        scope: Permission.Scope
    ): PermissionResult

    suspend fun getPermissions(account: Snowflake): Map<Permission, Permission.Scope>

    enum class PermissionResult {
        SUCCESS,
        PERMISSION_NOT_FOUND,
        ACCOUNT_NOT_FOUND
    }
}

class SimplePermissionManager(
    private val provider: SQLProvider,
    private val messenger: Messenger,
    private val accounts: AccountManager,
) : PermissionManager, ImperiumApplication.Listener {

    private val cache =
        CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10L))
            .build<Snowflake, Map<Permission, Permission.Scope>>()

    override fun onImperiumInit() {
        provider.newTransaction { SchemaUtils.create(AccountPermissionTable) }
        messenger.consumer<PermissionChangeMessage> { refreshPermissions(it.account) }
    }

    override suspend fun getPermission(
        identity: Identity.Mindustry,
        permission: Permission
    ): Permission.Scope {
        val account = accounts.findByIdentity(identity) ?: return Permission.Scope.False
        return getPermissions(account.snowflake)[permission] ?: Permission.Scope.False
    }

    override suspend fun getPermission(
        account: Snowflake,
        permission: Permission
    ): Permission.Scope = getPermissions(account)[permission] ?: Permission.Scope.False

    override suspend fun setPermission(
        account: Snowflake,
        permission: Permission,
        scope: Permission.Scope
    ): PermissionManager.PermissionResult {
        val result =
            provider.newSuspendTransaction {
                if (!accounts.existsBySnowflake(account)) {
                    return@newSuspendTransaction PermissionManager.PermissionResult
                        .ACCOUNT_NOT_FOUND
                }
                when (scope) {
                    is Permission.Scope.False -> {
                        val rows =
                            AccountPermissionTable.deleteWhere {
                                (AccountPermissionTable.account eq account) and
                                    (AccountPermissionTable.permission eq permission)
                            }
                        if (rows == 0) {
                            return@newSuspendTransaction PermissionManager.PermissionResult
                                .PERMISSION_NOT_FOUND
                        }
                    }
                    is Permission.Scope.True -> {
                        AccountPermissionTable.upsert {
                            it[AccountPermissionTable.account] = account
                            it[AccountPermissionTable.permission] = permission
                            it[AccountPermissionTable.scope] = "*"
                        }
                    }
                    is Permission.Scope.Some -> {
                        AccountPermissionTable.upsert {
                            it[AccountPermissionTable.account] = account
                            it[AccountPermissionTable.permission] = permission
                            it[AccountPermissionTable.scope] = scope.servers.joinToString(",")
                        }
                    }
                }

                PermissionManager.PermissionResult.SUCCESS
            }

        if (result == PermissionManager.PermissionResult.SUCCESS) {
            refreshPermissions(account)
            messenger.publish(PermissionChangeMessage(account))
        }

        return result
    }

    override suspend fun getPermissions(account: Snowflake): Map<Permission, Permission.Scope> {
        var permissions = cache.getIfPresent(account)
        if (permissions == null) {
            permissions =
                provider.newSuspendTransaction {
                    AccountPermissionTable.slice(
                            AccountPermissionTable.permission, AccountPermissionTable.scope)
                        .select { AccountPermissionTable.account eq account }
                        .associate { it[AccountPermissionTable.permission] to it.toScope() }
                }
            cache.put(account, permissions)
        }
        return permissions
    }

    private suspend fun refreshPermissions(account: Snowflake) {
        cache.invalidate(account)
        getPermissions(account)
    }

    private fun ResultRow.toScope(): Permission.Scope {
        val value = this[AccountPermissionTable.scope]
        return if (value == "*") Permission.Scope.True
        else Permission.Scope.Some(value.split(",").filter(String::isBlank).toSet())
    }

    @Serializable private data class PermissionChangeMessage(val account: Snowflake) : Message
}
