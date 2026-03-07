// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.account

import java.net.InetAddress

data class SessionKey(val uuid: Long, val usid: Long, val address: InetAddress)
