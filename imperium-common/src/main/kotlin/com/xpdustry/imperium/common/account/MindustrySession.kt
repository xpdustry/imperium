// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.account

import java.time.Instant

data class MindustrySession(
    val key: SessionKey,
    val account: Int,
    val creation: Instant,
    val expiration: Instant,
    val lastLogin: Instant,
)

suspend fun MindustrySessionService.selectAccount(accounts: AccountService, key: SessionKey): Account? =
    selectByKey(key)?.let { accounts.selectById(it.account) }
