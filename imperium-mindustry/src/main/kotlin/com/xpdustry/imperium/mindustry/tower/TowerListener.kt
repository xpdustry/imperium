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
package com.xpdustry.imperium.mindustry.tower

import arc.func.Func
import arc.graphics.Color
import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.component.ListComponent.components
import com.xpdustry.distributor.api.component.NumberComponent.number
import com.xpdustry.distributor.api.component.TextComponent.space
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.component.style.ComponentColor
import com.xpdustry.distributor.api.gui.popup.PopupManager
import com.xpdustry.distributor.api.gui.popup.PopupPane
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.distributor.api.scheduler.Cancellable
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.mindustry.game.MenuToPlayEvent
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.getItemIcon
import java.text.DecimalFormat
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import mindustry.Vars
import mindustry.content.Fx
import mindustry.content.Items
import mindustry.content.StatusEffects
import mindustry.content.UnitTypes
import mindustry.game.EventType
import mindustry.game.EventType.PlayerJoin
import mindustry.gen.Call
import mindustry.net.Administration
import mindustry.type.Item
import mindustry.type.UnitType
import mindustry.type.unit.MissileUnitType
import mindustry.world.blocks.storage.CoreBlock
import mindustry.world.blocks.units.Reconstructor

class TowerListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val plugin = instances.get<MindustryPlugin>()
    private val downgrades = mutableMapOf<UnitType, UnitType>()
    private val popup = PopupManager.create(plugin)
    private var increase: Cancellable? = null
        set(value) {
            field?.cancel()
            field = value
        }

    init {
        popup.updateInterval = 5.seconds.toJavaDuration()
        popup.addTransformer { ctx ->
            ctx.pane.alignementX = PopupPane.AlignementX.LEFT
            ctx.pane.setContent(
                "Enemy health multiplier x${DECIMAL_FORMAT.format(Vars.state.rules.waveTeam.rules().unitHealthMultiplier)}"
            )
        }
    }

    override fun onImperiumInit() {
        Vars.pathfinder = TowerPathfinder(plugin)
        Vars.spawner = TowerWaveSpawner()

        // Do not allow the deposit of items in any core
        Vars.netServer.admins.addActionFilter {
            return@addActionFilter !((it.type == Administration.ActionType.depositItem ||
                it.type == Administration.ActionType.withdrawItem) && it.tile.block() is CoreBlock)
        }

        Vars.content
            .units()
            .asSequence()
            .filter { it !is MissileUnitType }
            .forEach { type ->
                val previous = type.controller
                type.controller = Func { unit ->
                    if (unit.team() == Vars.state.rules.waveTeam) GroundTowerAI() else previous.get(unit)
                }
            }

        Vars.content.blocks().asSequence().filterIsInstance<Reconstructor>().forEach { block ->
            block.upgrades.forEach { upgrade ->
                if (upgrade[1] in downgrades) {
                    LOGGER.warn(
                        "Duplicate downgrade for {}, got {} and {}",
                        upgrade[1].name,
                        downgrades[upgrade[1]]!!.name,
                        upgrade[0].name,
                    )
                } else {
                    downgrades[upgrade[1]] = upgrade[0]
                }
            }
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoin) {
        popup.create(event.player).show()
    }

    @EventHandler
    fun onMenuToPlayEvent(event: MenuToPlayEvent) {
        increase =
            Distributor.get()
                .pluginScheduler
                .schedule(plugin)
                .delay(1L, MindustryTimeUnit.MINUTES)
                .repeat(1L, MindustryTimeUnit.MINUTES)
                .execute { cancellable ->
                    if (!Vars.state.isPlaying) {
                        cancellable.cancel()
                        return@execute
                    }
                    Vars.state.rules.waveTeam.rules().unitHealthMultiplier *= MULTIPLIER_PER_MINUTE
                }

        Entities.getUnits().forEach { unit ->
            if (unit.team() == Vars.state.rules.waveTeam) unit.controller(GroundTowerAI())
        }
    }

    @EventHandler
    fun onUnitDestroyEvent(event: EventType.UnitDestroyEvent) {
        if (event.unit.team() != Vars.state.rules.waveTeam) return

        val bounty = getItemBounty(event.unit.type())
        Distributor.get()
            .audienceProvider
            .players
            .showLabel(bounty.toBountyComponent(), event.unit.x(), event.unit.y(), 2.seconds.toJavaDuration())
        bounty.forEach { (item, count) -> Vars.state.rules.defaultTeam.core()?.items()?.add(item, count) }

        val downgrade = downgrades[event.unit.type()] ?: return
        val unit = downgrade.create(Vars.state.rules.waveTeam)
        unit.set(event.unit.x(), event.unit.y())
        unit.rotation(event.unit.rotation())
        unit.apply(StatusEffects.slow, MindustryTimeUnit.TICKS.convert(5L, MindustryTimeUnit.SECONDS).toFloat())
        unit.add()
        Call.effect(Fx.spawn, event.unit.x(), event.unit.y(), 0F, Color.red)
    }

    private fun Map<Item, Int>.toBountyComponent(): Component =
        components()
            .apply {
                val color = ComponentColor.from(Vars.state.rules.defaultTeam.color)
                entries
                    .sortedBy { it.key.id }
                    .forEachIndexed { index, (item, count) ->
                        append(text('+', color))
                        append(number(count, color))
                        append(space())
                        append(text(getItemIcon(item)))
                        if (index + 1 < entries.size) {
                            append(text(" ", color))
                        }
                    }
            }
            .build()

    private fun getItemBounty(type: UnitType): Map<Item, Int> {
        val bounty = HashMap<Item, Int>()
        when (TIERS[type] ?: UnitTier.TIER_1) {
            UnitTier.TIER_1 -> {
                bounty[Items.copper] = 20
                bounty[Items.lead] = 20
                bounty[Items.silicon] = 5
                when (Random.nextInt(1, 4)) {
                    1 -> bounty[Items.graphite] = 10
                    2 -> bounty[Items.metaglass] = 5
                    3 -> bounty[Items.titanium] = 10
                }
            }
            UnitTier.TIER_2 -> {
                bounty[Items.copper] = 30
                bounty[Items.lead] = 30
                bounty[Items.silicon] = 10
                when (Random.nextInt(1, 4)) {
                    1 -> bounty[Items.graphite] = 20
                    2 -> bounty[Items.metaglass] = 10
                    3 -> bounty[Items.titanium] = 20
                }
            }
            UnitTier.TIER_3 -> {
                bounty[Items.copper] = 50
                bounty[Items.lead] = 50
                bounty[Items.silicon] = 30
                bounty[Items.phaseFabric] = 3
                bounty[Items.surgeAlloy] = 3
                when (Random.nextInt(1, 6)) {
                    1 -> bounty[Items.graphite] = 30
                    2 -> bounty[Items.metaglass] = 20
                    3 -> bounty[Items.titanium] = 30
                    4 -> bounty[Items.plastanium] = 50
                    5 -> bounty[Items.thorium] = 50
                }
            }
            UnitTier.TIER_4 -> {
                bounty[Items.copper] = 200
                bounty[Items.lead] = 200
                bounty[Items.silicon] = 50
                bounty[Items.phaseFabric] = 20
                bounty[Items.surgeAlloy] = 40
                when (Random.nextInt(1, 6)) {
                    1 -> bounty[Items.graphite] = 30
                    2 -> bounty[Items.metaglass] = 30
                    3 -> bounty[Items.titanium] = 30
                    4 -> bounty[Items.plastanium] = 80
                    5 -> bounty[Items.thorium] = 80
                }
            }
            UnitTier.TIER_5 -> {
                bounty[Items.copper] = 300
                bounty[Items.lead] = 300
                bounty[Items.silicon] = 100
                bounty[Items.phaseFabric] = 30
                bounty[Items.surgeAlloy] = 60
                when (Random.nextInt(1, 6)) {
                    1 -> bounty[Items.graphite] = 40
                    2 -> bounty[Items.metaglass] = 40
                    3 -> bounty[Items.titanium] = 40
                    4 -> bounty[Items.plastanium] = 120
                    5 -> bounty[Items.thorium] = 120
                }
            }
        }
        return bounty
    }

    enum class UnitTier {
        TIER_1,
        TIER_2,
        TIER_3,
        TIER_4,
        TIER_5,
    }

    enum class UnitTree {
        DAGGER,
        NOVA,
        CRAWLER,
        MONO,
        FLARE,
    }

    companion object {
        private val LOGGER by LoggerDelegate()
        private val DECIMAL_FORMAT = DecimalFormat("#.##")
        private const val MULTIPLIER_PER_MINUTE = 1.03F

        private val TIERS: Map<UnitType, UnitTier>
        private val TREES: Map<UnitType, UnitTree>

        init {
            val tiers = mutableMapOf<UnitType, UnitTier>()
            val trees = mutableMapOf<UnitType, UnitTree>()

            fun addUnit(type: UnitType, tier: UnitTier, tree: UnitTree) {
                tiers[type] = tier
                trees[type] = tree
            }

            addUnit(UnitTypes.dagger, UnitTier.TIER_1, UnitTree.DAGGER)
            addUnit(UnitTypes.mace, UnitTier.TIER_2, UnitTree.DAGGER)
            addUnit(UnitTypes.fortress, UnitTier.TIER_3, UnitTree.DAGGER)
            addUnit(UnitTypes.scepter, UnitTier.TIER_4, UnitTree.DAGGER)
            addUnit(UnitTypes.reign, UnitTier.TIER_5, UnitTree.DAGGER)

            addUnit(UnitTypes.crawler, UnitTier.TIER_1, UnitTree.CRAWLER)
            addUnit(UnitTypes.atrax, UnitTier.TIER_2, UnitTree.CRAWLER)
            addUnit(UnitTypes.spiroct, UnitTier.TIER_3, UnitTree.CRAWLER)
            addUnit(UnitTypes.arkyid, UnitTier.TIER_4, UnitTree.CRAWLER)
            addUnit(UnitTypes.toxopid, UnitTier.TIER_5, UnitTree.CRAWLER)

            addUnit(UnitTypes.nova, UnitTier.TIER_1, UnitTree.NOVA)
            addUnit(UnitTypes.pulsar, UnitTier.TIER_2, UnitTree.NOVA)
            addUnit(UnitTypes.quasar, UnitTier.TIER_3, UnitTree.NOVA)
            addUnit(UnitTypes.vela, UnitTier.TIER_4, UnitTree.NOVA)
            addUnit(UnitTypes.corvus, UnitTier.TIER_5, UnitTree.NOVA)

            addUnit(UnitTypes.mono, UnitTier.TIER_1, UnitTree.MONO)
            addUnit(UnitTypes.poly, UnitTier.TIER_2, UnitTree.MONO)
            addUnit(UnitTypes.mega, UnitTier.TIER_3, UnitTree.MONO)
            addUnit(UnitTypes.quad, UnitTier.TIER_4, UnitTree.MONO)
            addUnit(UnitTypes.oct, UnitTier.TIER_5, UnitTree.MONO)

            addUnit(UnitTypes.flare, UnitTier.TIER_1, UnitTree.FLARE)
            addUnit(UnitTypes.horizon, UnitTier.TIER_2, UnitTree.FLARE)
            addUnit(UnitTypes.zenith, UnitTier.TIER_3, UnitTree.FLARE)
            addUnit(UnitTypes.antumbra, UnitTier.TIER_4, UnitTree.FLARE)
            addUnit(UnitTypes.eclipse, UnitTier.TIER_5, UnitTree.FLARE)

            TIERS = tiers
            TREES = trees
        }
    }
}
