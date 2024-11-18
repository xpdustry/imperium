package com.xpdustry.imperium.mindustry.event

import mindustry.world.block.storage.Vault
import mindustry.world.meta.Building

private val buildingRarityMap = mutableMapOf<Building, Int>()

var Building.rarity: Int?
    get() = buildingRarityMap[this]
    set(value) {
        if (value == null) {
            buildingRarityMap.remove(this)
        } else {
            buildingRarityMap[this] = value
        }
    }