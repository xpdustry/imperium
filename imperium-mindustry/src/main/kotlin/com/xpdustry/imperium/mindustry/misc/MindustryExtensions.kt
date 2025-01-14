/*
 * Imperium, the software collection powering the Chaotic Neutral network.
 * Copyright (C) 2024  Xpdustry
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
package com.xpdustry.imperium.mindustry.misc

import arc.graphics.Color
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.common.version.MindustryVersion
import java.net.InetAddress
import mindustry.Vars
import mindustry.content.Items
import mindustry.core.GameState
import mindustry.core.Version
import mindustry.game.Gamemode
import mindustry.gen.Building
import mindustry.gen.Iconc
import mindustry.net.Administration
import mindustry.type.Item
import mindustry.world.Block
import mindustry.world.blocks.sandbox.ItemSource
import mindustry.world.blocks.sandbox.ItemVoid
import mindustry.world.blocks.sandbox.LiquidSource
import mindustry.world.blocks.sandbox.LiquidVoid
import mindustry.world.blocks.sandbox.PowerSource
import mindustry.world.blocks.sandbox.PowerVoid
import mindustry.world.blocks.storage.CoreBlock
import mindustry.world.blocks.storage.StorageBlock

fun getMindustryServerInfo(): Discovery.Data.Mindustry =
    Discovery.Data.Mindustry(
        Administration.Config.serverName.string(),
        // Our servers run within a pterodactyl container, so we can use the SERVER_IP environment
        // variable
        System.getenv("SERVER_IP")?.toInetAddress() ?: InetAddress.getLocalHost(),
        Administration.Config.port.num(),
        Vars.state.map.name(),
        Administration.Config.desc.string(),
        Vars.state.wave,
        Entities.getPlayers().size,
        Vars.netServer.admins.playerLimit,
        getMindustryVersion(),
        getGameMode(),
        Vars.state.rules.modeName,
        when (Vars.state.state!!) {
            GameState.State.playing -> Discovery.Data.Mindustry.State.PLAYING
            GameState.State.paused -> Discovery.Data.Mindustry.State.PAUSED
            GameState.State.menu -> Discovery.Data.Mindustry.State.STOPPED
        },
    )

fun getGameMode(): Discovery.Data.Mindustry.Gamemode =
    when (Vars.state.rules.mode()!!) {
        Gamemode.attack -> Discovery.Data.Mindustry.Gamemode.ATTACK
        Gamemode.pvp -> Discovery.Data.Mindustry.Gamemode.PVP
        Gamemode.sandbox -> Discovery.Data.Mindustry.Gamemode.SANDBOX
        Gamemode.survival -> Discovery.Data.Mindustry.Gamemode.SURVIVAL
        Gamemode.editor -> Discovery.Data.Mindustry.Gamemode.EDITOR
    }

fun getMindustryVersion(): MindustryVersion =
    MindustryVersion(Version.number, Version.build.coerceAtLeast(0), Version.revision, getVersionType())

// Yes, this is a mess
private fun getVersionType(): MindustryVersion.Type =
    if (Version.build == -1) {
        MindustryVersion.Type.CUSTOM
    } else
        when (Version.modifier.lowercase()) {
            "alpha" -> MindustryVersion.Type.ALPHA
            else ->
                when (Version.type) {
                    "official" -> MindustryVersion.Type.OFFICIAL
                    "bleeding-edge" -> MindustryVersion.Type.BLEEDING_EDGE
                    else -> MindustryVersion.Type.CUSTOM
                }
        }

val Building.isCoreBuilding: Boolean
    get() = block() is CoreBlock || (this is StorageBlock.StorageBuild && linkedCore != null)

val Block.isSourceBlock: Boolean
    get() = this is ItemSource || this is LiquidSource || this is PowerSource

val Block.isVoidBlock: Boolean
    get() = this is ItemVoid || this is LiquidVoid || this is PowerVoid

fun Color.toHexString(): String = String.format("#%06x", rgb888())

fun getItemIcon(item: Item): String =
    when (item) {
        Items.scrap -> Iconc.itemScrap.toString()
        Items.copper -> Iconc.itemCopper.toString()
        Items.lead -> Iconc.itemLead.toString()
        Items.graphite -> Iconc.itemGraphite.toString()
        Items.coal -> Iconc.itemCoal.toString()
        Items.titanium -> Iconc.itemTitanium.toString()
        Items.thorium -> Iconc.itemThorium.toString()
        Items.silicon -> Iconc.itemSilicon.toString()
        Items.plastanium -> Iconc.itemPlastanium.toString()
        Items.phaseFabric -> Iconc.itemPhaseFabric.toString()
        Items.surgeAlloy -> Iconc.itemSurgeAlloy.toString()
        Items.sporePod -> Iconc.itemSporePod.toString()
        Items.sand -> Iconc.itemSand.toString()
        Items.blastCompound -> Iconc.itemBlastCompound.toString()
        Items.pyratite -> Iconc.itemPyratite.toString()
        Items.metaglass -> Iconc.itemMetaglass.toString()
        Items.beryllium -> Iconc.itemBeryllium.toString()
        Items.tungsten -> Iconc.itemTungsten.toString()
        Items.oxide -> Iconc.itemOxide.toString()
        Items.carbide -> Iconc.itemCarbide.toString()
        Items.fissileMatter -> Iconc.itemFissileMatter.toString()
        Items.dormantCyst -> Iconc.itemDormantCyst.toString()
        else -> "<${item.name}>"
    }
