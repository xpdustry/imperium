// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.store

import com.xpdustry.imperium.common.account.Account
import com.xpdustry.imperium.common.account.Achievement

data class StoredAccount(val account: Account, val achievements: Set<Achievement>, val metadata: Map<String, String>)
