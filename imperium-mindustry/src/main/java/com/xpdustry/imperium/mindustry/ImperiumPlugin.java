package com.xpdustry.imperium.mindustry;

import arc.Core;
import com.xpdustry.distributor.api.Distributor;
import com.xpdustry.distributor.api.annotation.PluginAnnotationProcessor;
import com.xpdustry.distributor.api.component.render.ComponentRendererProvider;
import com.xpdustry.distributor.api.plugin.AbstractMindustryPlugin;
import com.xpdustry.distributor.api.translation.BundleTranslationSource;
import com.xpdustry.distributor.api.translation.ResourceBundles;
import com.xpdustry.distributor.api.translation.TranslationSource;
import com.xpdustry.distributor.api.util.Priority;
import com.xpdustry.imperium.common.CommonModule;
import com.xpdustry.imperium.common.config.ImperiumConfig;
import com.xpdustry.imperium.common.content.MindustryGamemode;
import com.xpdustry.imperium.common.factory.ObjectFactory;
import com.xpdustry.imperium.common.lifecycle.*;
import com.xpdustry.imperium.common.time.TimeRenderer;
import com.xpdustry.imperium.mindustry.account.AccountCommand;
import com.xpdustry.imperium.mindustry.account.AccountListener;
import com.xpdustry.imperium.mindustry.account.AchievementCommand;
import com.xpdustry.imperium.mindustry.account.UserSettingsCommand;
import com.xpdustry.imperium.mindustry.backport.EnforceAutopauseOnLoadBackport;
import com.xpdustry.imperium.mindustry.backport.NoApplicationListenerSkipBackport;
import com.xpdustry.imperium.mindustry.chat.BridgeChatMessageListener;
import com.xpdustry.imperium.mindustry.chat.ChatCommand;
import com.xpdustry.imperium.mindustry.chat.FlexListener;
import com.xpdustry.imperium.mindustry.chat.HereCommand;
import com.xpdustry.imperium.mindustry.command.CommandAnnotationScanner;
import com.xpdustry.imperium.mindustry.command.HelpCommand;
import com.xpdustry.imperium.mindustry.component.ImperiumComponentRendererProvider;
import com.xpdustry.imperium.mindustry.config.ConventionListener;
import com.xpdustry.imperium.mindustry.control.ControlListener;
import com.xpdustry.imperium.mindustry.formation.FormationListener;
import com.xpdustry.imperium.mindustry.game.AlertListener;
import com.xpdustry.imperium.mindustry.game.AntiGriefListener;
import com.xpdustry.imperium.mindustry.game.ChangelogCommand;
import com.xpdustry.imperium.mindustry.game.DayNighCycleListener;
import com.xpdustry.imperium.mindustry.game.GameListener;
import com.xpdustry.imperium.mindustry.game.ImperiumLogicListener;
import com.xpdustry.imperium.mindustry.game.LogicListener;
import com.xpdustry.imperium.mindustry.game.PauseListener;
import com.xpdustry.imperium.mindustry.game.RatingListener;
import com.xpdustry.imperium.mindustry.game.TeamCommand;
import com.xpdustry.imperium.mindustry.game.TipListener;
import com.xpdustry.imperium.mindustry.history.HistoryCommand;
import com.xpdustry.imperium.mindustry.metrics.MetricsListener;
import com.xpdustry.imperium.mindustry.misc.ImperiumMetadataChunkReader;
import com.xpdustry.imperium.mindustry.permission.ImperiumPermissionListener;
import com.xpdustry.imperium.mindustry.security.AdminRequestListener;
import com.xpdustry.imperium.mindustry.security.AntiEvadeListener;
import com.xpdustry.imperium.mindustry.security.GatekeeperListener;
import com.xpdustry.imperium.mindustry.security.ModerationCommand;
import com.xpdustry.imperium.mindustry.security.NoHornyListener;
import com.xpdustry.imperium.mindustry.security.PunishmentListener;
import com.xpdustry.imperium.mindustry.security.ReportCommand;
import com.xpdustry.imperium.mindustry.security.VoteKickCommand;
import com.xpdustry.imperium.mindustry.telemetry.DumpCommand;
import com.xpdustry.imperium.mindustry.world.CoreBlockListener;
import com.xpdustry.imperium.mindustry.world.ExcavateCommand;
import com.xpdustry.imperium.mindustry.world.HubListener;
import com.xpdustry.imperium.mindustry.world.KillAllCommand;
import com.xpdustry.imperium.mindustry.world.MapListener;
import com.xpdustry.imperium.mindustry.world.ResourceHudListener;
import com.xpdustry.imperium.mindustry.world.RockTheVoteCommand;
import com.xpdustry.imperium.mindustry.world.SaveCommand;
import com.xpdustry.imperium.mindustry.world.SpawnCommand;
import com.xpdustry.imperium.mindustry.world.SwitchCommand;
import com.xpdustry.imperium.mindustry.world.WaveCommand;
import com.xpdustry.imperium.mindustry.world.WelcomeListener;
import com.xpdustry.imperium.mindustry.world.WorldEditCommand;
import mindustry.io.SaveVersion;

public final class ImperiumPlugin extends AbstractMindustryPlugin {

    private LifecycleService lifecycle;
    private ObjectFactory factory;

    @Override
    public void onInit() {
        this.addListener(new EnforceAutopauseOnLoadBackport(this));
        this.addListener(new NoApplicationListenerSkipBackport());
        SaveVersion.addCustomChunk("imperium", ImperiumMetadataChunkReader.INSTANCE);

        // TODO Move factory and lifecycle creation to fields
        this.factory = ObjectFactory.create(new CommonModule(), new LifecycleModule(), new MindustryModule(this));
        this.lifecycle = this.factory.get(LifecycleService.class);
        for (final var object : this.factory.objects()) {
            if (object instanceof LifecycleListener listener) {
                this.lifecycle.addListener(listener);
            }
        }

        this.addListener(ConventionListener.class);
        this.addListener(GatekeeperListener.class);
        this.addListener(AccountListener.class);
        this.addListener(AccountCommand.class);
        this.addListener(ChatCommand.class);
        this.addListener(HistoryCommand.class);
        this.addListener(BridgeChatMessageListener.class);
        this.addListener(ReportCommand.class);
        this.addListener(NoHornyListener.class);
        this.addListener(AdminRequestListener.class);
        this.addListener(PunishmentListener.class);
        this.addListener(MapListener.class);
        this.addListener(VoteKickCommand.class);
        this.addListener(ExcavateCommand.class);
        this.addListener(RockTheVoteCommand.class);
        this.addListener(CoreBlockListener.class);
        this.addListener(HelpCommand.class);
        this.addListener(WaveCommand.class);
        this.addListener(KillAllCommand.class);
        this.addListener(DumpCommand.class);
        this.addListener(SwitchCommand.class);
        this.addListener(UserSettingsCommand.class);
        this.addListener(WelcomeListener.class);
        this.addListener(ResourceHudListener.class);
        this.addListener(ImperiumLogicListener.class);
        this.addListener(AntiEvadeListener.class);
        this.addListener(GameListener.class);
        this.addListener(TipListener.class);
        this.addListener(RatingListener.class);
        this.addListener(SpawnCommand.class);
        this.addListener(WorldEditCommand.class);
        this.addListener(HereCommand.class);
        this.addListener(ModerationCommand.class);
        this.addListener(AlertListener.class);
        this.addListener(TeamCommand.class);
        this.addListener(FormationListener.class);
        this.addListener(ControlListener.class);
        this.addListener(PauseListener.class);
        this.addListener(AchievementCommand.class);
        this.addListener(LogicListener.class);
        this.addListener(SaveCommand.class);
        this.addListener(AntiGriefListener.class);
        this.addListener(FlexListener.class);
        this.addListener(MetricsListener.class);
        this.addListener(ChangelogCommand.class);
        this.addListener(DayNighCycleListener.class);
        this.addListener(ImperiumPermissionListener.class);

        final var config = factory.get(ImperiumConfig.class);

        final var bundle = BundleTranslationSource.create(config.language());
        bundle.registerAll(
                ResourceBundles.fromClasspathDirectory(
                        ImperiumPlugin.class, "com/xpdustry/imperium/mindustry/bundles/", "bundle"),
                ResourceBundles::getMessageFormatTranslation);
        Distributor.get().getServiceManager().register(this, TranslationSource.class, bundle, Priority.NORMAL);

        final var renderer = new ImperiumComponentRendererProvider(factory.get(TimeRenderer.class));
        Distributor.get()
                .getServiceManager()
                .register(this, ComponentRendererProvider.class, renderer, Priority.NORMAL);

        if (config.mindustry().gamemode() == MindustryGamemode.HUB) {
            this.addListener(HubListener.class);
        } else {
            Core.settings.remove("totalPlayers");
        }
    }

    @Override
    public void onLoad() {
        final var processor = PluginAnnotationProcessor.compose(
                this.factory.get(CommandAnnotationScanner.class),
                PluginAnnotationProcessor.tasks(this),
                PluginAnnotationProcessor.events(this),
                PluginAnnotationProcessor.triggers(this));
        for (final var listener : this.lifecycle.listeners()) {
            processor.process(listener);
        }

        this.lifecycle.load();
        this.getLogger().info("Imperium plugin Loaded!");
    }

    @Override
    public void onExit() {
        this.lifecycle.exit(PlatformExitCode.SUCCESS);
    }

    private void addListener(final Class<? extends LifecycleListener> listener) {
        this.lifecycle.addListener(this.factory.get(listener));
    }
}
