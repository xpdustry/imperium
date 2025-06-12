package com.xpdustry.imperium.backend;

import com.xpdustry.imperium.common.CommonModule;
import com.xpdustry.imperium.common.account.AccountModule;
import com.xpdustry.imperium.common.annotation.AnnotationScanner;
import com.xpdustry.imperium.common.database.DatabaseModule;
import com.xpdustry.imperium.common.factory.ObjectFactory;
import com.xpdustry.imperium.common.lifecycle.LifecycleModule;
import com.xpdustry.imperium.common.lifecycle.LifecycleService;
import com.xpdustry.imperium.common.message.MessageModule;
import com.xpdustry.imperium.common.password.PasswordModule;
import com.xpdustry.imperium.discord.DiscordModule;
import com.xpdustry.imperium.discord.account.RoleSyncListener;
import com.xpdustry.imperium.discord.bridge.MindustryBridgeListener;
import com.xpdustry.imperium.discord.commands.*;
import com.xpdustry.imperium.discord.content.MindustryContentListener;
import com.xpdustry.imperium.discord.metrics.MetricsListener;
import com.xpdustry.imperium.discord.rest.RestListener;
import com.xpdustry.imperium.discord.security.PunishmentListener;
import com.xpdustry.imperium.discord.security.ReportListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BackendLauncher {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackendLauncher.class);

    public static void main(final String[] ignored) {
        final var factory = ObjectFactory.create(
                new CommonModule(),
                new LifecycleModule(),
                new DiscordModule(),
                new AccountModule(),
                new DatabaseModule(),
                new MessageModule(),
                new PasswordModule());
        final var lifecycle = factory.get(LifecycleService.class);

        lifecycle.addListener(MindustryBridgeListener.class);
        lifecycle.addListener(PingCommand.class);
        lifecycle.addListener(ServerCommand.class);
        lifecycle.addListener(ReportListener.class);
        lifecycle.addListener(MapCommand.class);
        lifecycle.addListener(VerifyCommand.class);
        lifecycle.addListener(ModerationCommand.class);
        lifecycle.addListener(PunishmentListener.class);
        lifecycle.addListener(RoleSyncListener.class);
        lifecycle.addListener(PlayerCommand.class);
        lifecycle.addListener(AccountCommand.class);
        lifecycle.addListener(WhitelistCommand.class);
        lifecycle.addListener(RestListener.class);
        lifecycle.addListener(MapSearchCommand.class);
        lifecycle.addListener(MapSubmitCommand.class);
        lifecycle.addListener(MindustryContentListener.class);
        lifecycle.addListener(HistoryCommand.class);
        lifecycle.addListener(MetricsListener.class);

        // Make them use ObjectFactory.collect
        final var scanners = new AnnotationScanner[] {
            factory.get(AnnotationScanner.class, "slash"),
            factory.get(AnnotationScanner.class, "menu"),
            factory.get(AnnotationScanner.class, "modal"),
        };

        lifecycle.load();

        for (final var listener : lifecycle.listeners()) {
            for (final var scanner : scanners) {
                scanner.scan(listener);
            }
        }

        for (final var scanner : scanners) {
            scanner.process();
        }

        LOGGER.info("Imperium backend loaded.");
    }
}
