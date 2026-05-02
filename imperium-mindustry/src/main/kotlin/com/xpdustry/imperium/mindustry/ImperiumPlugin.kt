// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry

import arc.Application
import arc.ApplicationListener
import arc.Core
import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.annotation.PluginAnnotationProcessor
import com.xpdustry.distributor.api.component.render.ComponentRendererProvider
import com.xpdustry.distributor.api.plugin.AbstractMindustryPlugin
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.distributor.api.translation.BundleTranslationSource
import com.xpdustry.distributor.api.translation.ResourceBundles
import com.xpdustry.distributor.api.translation.TranslationSource
import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.imperium.common.application.BaseImperiumApplication
import com.xpdustry.imperium.common.application.ExitStatus
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.registerCommonModule
import com.xpdustry.imperium.common.webhook.WebhookChannel
import com.xpdustry.imperium.common.webhook.WebhookMessage
import com.xpdustry.imperium.common.webhook.WebhookMessageSender
import com.xpdustry.imperium.mindustry.account.AccountCommand
import com.xpdustry.imperium.mindustry.account.AccountListener
import com.xpdustry.imperium.mindustry.account.AchievementCommand
import com.xpdustry.imperium.mindustry.account.UserSettingsCommand
import com.xpdustry.imperium.mindustry.chat.BridgeChatMessageListener
import com.xpdustry.imperium.mindustry.chat.ChatCommand
import com.xpdustry.imperium.mindustry.chat.HereCommand
import com.xpdustry.imperium.mindustry.chat.MindustryChatListener
import com.xpdustry.imperium.mindustry.command.CommandAnnotationScanner
import com.xpdustry.imperium.mindustry.command.HelpCommand
import com.xpdustry.imperium.mindustry.command.YesCommand
import com.xpdustry.imperium.mindustry.config.ConventionListener
import com.xpdustry.imperium.mindustry.control.ControlListener
import com.xpdustry.imperium.mindustry.formation.FormationListener
import com.xpdustry.imperium.mindustry.game.AlertListener
import com.xpdustry.imperium.mindustry.game.AntiGriefListener
import com.xpdustry.imperium.mindustry.game.ChangelogCommand
import com.xpdustry.imperium.mindustry.game.DayNightCycleListener
import com.xpdustry.imperium.mindustry.game.GameListener
import com.xpdustry.imperium.mindustry.game.ImperiumLogicListener
import com.xpdustry.imperium.mindustry.game.LogicListener
import com.xpdustry.imperium.mindustry.game.PauseListener
import com.xpdustry.imperium.mindustry.game.RatingListener
import com.xpdustry.imperium.mindustry.game.TeamCommand
import com.xpdustry.imperium.mindustry.game.TipListener
import com.xpdustry.imperium.mindustry.history.HistoryCommand
import com.xpdustry.imperium.mindustry.metrics.MetricsListener
import com.xpdustry.imperium.mindustry.misc.ImperiumMetadataChunkReader
import com.xpdustry.imperium.mindustry.monitoring.BlockHoundService
import com.xpdustry.imperium.mindustry.permission.ImperiumPermissionListener
import com.xpdustry.imperium.mindustry.security.AdminRequestListener
import com.xpdustry.imperium.mindustry.security.AntiEvadeListener
import com.xpdustry.imperium.mindustry.security.GatekeeperListener
import com.xpdustry.imperium.mindustry.security.ModerationCommand
import com.xpdustry.imperium.mindustry.security.NoHornyListener
import com.xpdustry.imperium.mindustry.security.PunishmentListener
import com.xpdustry.imperium.mindustry.security.ReportCommand
import com.xpdustry.imperium.mindustry.security.VoteKickCommand
import com.xpdustry.imperium.mindustry.telemetry.DumpCommand
import com.xpdustry.imperium.mindustry.world.CoreBlockListener
import com.xpdustry.imperium.mindustry.world.ExcavateCommand
import com.xpdustry.imperium.mindustry.world.HubListener
import com.xpdustry.imperium.mindustry.world.ItemCommand
import com.xpdustry.imperium.mindustry.world.KillAllCommand
import com.xpdustry.imperium.mindustry.world.MapListener
import com.xpdustry.imperium.mindustry.world.ResourceHudListener
import com.xpdustry.imperium.mindustry.world.RockTheVoteCommand
import com.xpdustry.imperium.mindustry.world.SaveCommand
import com.xpdustry.imperium.mindustry.world.SpawnCommand
import com.xpdustry.imperium.mindustry.world.SwitchCommand
import com.xpdustry.imperium.mindustry.world.WaveCommand
import com.xpdustry.imperium.mindustry.world.WelcomeListener
import com.xpdustry.imperium.mindustry.world.WorldEditCommand
import kotlin.reflect.KClass
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import mindustry.io.SaveVersion

class ImperiumPlugin : AbstractMindustryPlugin() {
    private val application = MindustryImperiumApplication(this)

    override fun onLoad() {
        application.register(BlockHoundService::class)

        SaveVersion.addCustomChunk("imperium", ImperiumMetadataChunkReader)

        application.createAll()

        registerService(
            TranslationSource::class,
            BundleTranslationSource.create(application.instances.get<ImperiumConfig>().language).apply {
                registerAll(
                    ResourceBundles.fromClasspathDirectory(
                        ImperiumPlugin::class.java,
                        "com/xpdustry/imperium/mindustry/bundles/",
                        "bundle",
                    ),
                    ResourceBundles::getMessageFormatTranslation,
                )
            },
        )

        registerService(ComponentRendererProvider::class, application.instances.get<ComponentRendererProvider>())

        sequenceOf(
                ConventionListener::class,
                GatekeeperListener::class,
                AccountListener::class,
                AccountCommand::class,
                ChatCommand::class,
                MindustryChatListener::class,
                HistoryCommand::class,
                BridgeChatMessageListener::class,
                ReportCommand::class,
                NoHornyListener::class,
                AdminRequestListener::class,
                PunishmentListener::class,
                MapListener::class,
                VoteKickCommand::class,
                ExcavateCommand::class,
                RockTheVoteCommand::class,
                CoreBlockListener::class,
                HelpCommand::class,
                YesCommand::class,
                WaveCommand::class,
                KillAllCommand::class,
                DumpCommand::class,
                SwitchCommand::class,
                UserSettingsCommand::class,
                WelcomeListener::class,
                ResourceHudListener::class,
                ImperiumLogicListener::class,
                AntiEvadeListener::class,
                GameListener::class,
                TipListener::class,
                RatingListener::class,
                SpawnCommand::class,
                WorldEditCommand::class,
                HereCommand::class,
                ModerationCommand::class,
                AlertListener::class,
                TeamCommand::class,
                FormationListener::class,
                ControlListener::class,
                PauseListener::class,
                AchievementCommand::class,
                LogicListener::class,
                SaveCommand::class,
                AntiGriefListener::class,
                MetricsListener::class,
                ChangelogCommand::class,
                DayNightCycleListener::class,
                ImperiumPermissionListener::class,
                ItemCommand::class,
            )
            .forEach(application::register)

        val gamemode = application.instances.get<ImperiumConfig>().mindustry.gamemode
        if (gamemode == MindustryGamemode.HUB) {
            application.register(HubListener::class)
        } else {
            Core.settings.remove("totalPlayers")
        }

        application.init()

        val processor =
            PluginAnnotationProcessor.compose(
                application.instances.get<CommandAnnotationScanner>(),
                PluginAnnotationProcessor.tasks(this),
                PluginAnnotationProcessor.events(this),
                PluginAnnotationProcessor.triggers(this),
            )

        application.listeners.forEach(processor::process)

        runBlocking {
            application.instances
                .get<WebhookMessageSender>()
                .send(WebhookChannel.CONSOLE, WebhookMessage(content = "The server has started."))
        }

        logger.info("Imperium plugin Loaded!")
    }

    override fun onExit() {
        application.exit(ExitStatus.EXIT)
    }

    private fun <T : Any> registerService(klass: KClass<T>, instance: T) {
        Distributor.get().serviceManager.register(this@ImperiumPlugin, klass.java, instance, Priority.NORMAL)
    }

    private inner class MindustryImperiumApplication(plugin: MindustryPlugin) :
        BaseImperiumApplication(
            logger,
            modules = {
                registerCommonModule()
                registerMindustryModule(plugin)
            },
        ) {
        private var exited = false

        override fun exit(status: ExitStatus) {
            if (exited) return
            exited = true
            runBlocking {
                instances
                    .get<WebhookMessageSender>()
                    .send(WebhookChannel.CONSOLE, WebhookMessage(content = "The server is exiting with $status code."))
            }
            super.exit(status)
            when (status) {
                ExitStatus.EXIT,
                ExitStatus.INIT_FAILURE -> Core.app.exit()
                ExitStatus.RESTART -> Core.app.restart()
            }
        }
    }
}

// Very hacky way to restart the server
private fun Application.restart() {
    exit()
    addListener(
        object : ApplicationListener {
            override fun dispose() {
                Core.settings.autosave()
                exitProcess(2)
            }
        }
    )
}
