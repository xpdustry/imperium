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
import com.xpdustry.imperium.common.account.AccountModule;
import com.xpdustry.imperium.common.config.ImperiumConfig;
import com.xpdustry.imperium.common.content.MindustryGamemode;
import com.xpdustry.imperium.common.database.DatabaseModule;
import com.xpdustry.imperium.common.factory.ObjectFactory;
import com.xpdustry.imperium.common.lifecycle.*;
import com.xpdustry.imperium.common.message.MessageModule;
import com.xpdustry.imperium.common.password.PasswordModule;
import com.xpdustry.imperium.common.time.TimeRenderer;
import com.xpdustry.imperium.mindustry.account.*;
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

    private ObjectFactory factory;
    private LifecycleService lifecycle;

    @Override
    public void onInit() {
        this.factory = ObjectFactory.create(
                new CommonModule(),
                new MindustryModule(this),
                new LifecycleModule(),
                new CachedAccountModule(),
                new AccountModule(),
                new DatabaseModule(),
                new MessageModule(),
                new PasswordModule());
        this.lifecycle = this.factory.get(LifecycleService.class);

        this.addListener(new EnforceAutopauseOnLoadBackport(this));
        this.addListener(new NoApplicationListenerSkipBackport());
        SaveVersion.addCustomChunk("imperium", ImperiumMetadataChunkReader.INSTANCE);

        this.lifecycle.addListener(ConventionListener.class);
        this.lifecycle.addListener(GatekeeperListener.class);
        this.lifecycle.addListener(AccountListener.class);
        this.lifecycle.addListener(AccountCommand.class);
        this.lifecycle.addListener(ChatCommand.class);
        this.lifecycle.addListener(HistoryCommand.class);
        this.lifecycle.addListener(BridgeChatMessageListener.class);
        this.lifecycle.addListener(ReportCommand.class);
        this.lifecycle.addListener(NoHornyListener.class);
        this.lifecycle.addListener(AdminRequestListener.class);
        this.lifecycle.addListener(PunishmentListener.class);
        this.lifecycle.addListener(MapListener.class);
        this.lifecycle.addListener(VoteKickCommand.class);
        this.lifecycle.addListener(ExcavateCommand.class);
        this.lifecycle.addListener(RockTheVoteCommand.class);
        this.lifecycle.addListener(CoreBlockListener.class);
        this.lifecycle.addListener(HelpCommand.class);
        this.lifecycle.addListener(WaveCommand.class);
        this.lifecycle.addListener(KillAllCommand.class);
        this.lifecycle.addListener(DumpCommand.class);
        this.lifecycle.addListener(SwitchCommand.class);
        this.lifecycle.addListener(UserSettingsCommand.class);
        this.lifecycle.addListener(WelcomeListener.class);
        this.lifecycle.addListener(ResourceHudListener.class);
        this.lifecycle.addListener(ImperiumLogicListener.class);
        this.lifecycle.addListener(AntiEvadeListener.class);
        this.lifecycle.addListener(GameListener.class);
        this.lifecycle.addListener(TipListener.class);
        this.lifecycle.addListener(RatingListener.class);
        this.lifecycle.addListener(SpawnCommand.class);
        this.lifecycle.addListener(WorldEditCommand.class);
        this.lifecycle.addListener(HereCommand.class);
        this.lifecycle.addListener(ModerationCommand.class);
        this.lifecycle.addListener(AlertListener.class);
        this.lifecycle.addListener(TeamCommand.class);
        this.lifecycle.addListener(FormationListener.class);
        this.lifecycle.addListener(ControlListener.class);
        this.lifecycle.addListener(PauseListener.class);
        this.lifecycle.addListener(AchievementCommand.class);
        this.lifecycle.addListener(LogicListener.class);
        this.lifecycle.addListener(SaveCommand.class);
        this.lifecycle.addListener(AntiGriefListener.class);
        this.lifecycle.addListener(FlexListener.class);
        this.lifecycle.addListener(MetricsListener.class);
        this.lifecycle.addListener(ChangelogCommand.class);
        this.lifecycle.addListener(DayNighCycleListener.class);
        this.lifecycle.addListener(ImperiumPermissionListener.class);
        this.lifecycle.addListener(LoginCommand.class);

        final var config = factory.get(ImperiumConfig.class);

        if (config.mindustry().gamemode() == MindustryGamemode.HUB) {
            this.lifecycle.addListener(HubListener.class);
        } else {
            Core.settings.remove("totalPlayers");
        }

        // TODO Separate listener ?
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
    }

    @Override
    public void onLoad() {
        this.lifecycle.load();

        final var processor = PluginAnnotationProcessor.compose(
                this.factory.get(CommandAnnotationScanner.class),
                PluginAnnotationProcessor.tasks(this),
                PluginAnnotationProcessor.events(this),
                PluginAnnotationProcessor.triggers(this));
        for (final var listener : this.lifecycle.listeners()) {
            processor.process(listener);
        }

        // TODO The logging should be inside load
        this.getLogger().info("Imperium plugin Loaded!");
    }

    @Override
    public void onExit() {
        this.lifecycle.exit(PlatformExitCode.SUCCESS);
    }
}
