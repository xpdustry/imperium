package com.xpdustry.foundation.common.network

import com.xpdustry.foundation.common.version.FoundationVersion

data class ServerInfo(
    val name: String,
    val mindustry: MindustryServerInfo?,
    val version: FoundationVersion,
)