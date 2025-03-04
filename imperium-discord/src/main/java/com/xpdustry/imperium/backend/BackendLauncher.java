package com.xpdustry.imperium.backend;

import com.xpdustry.imperium.common.CommonModule;
import com.xpdustry.imperium.common.annotation.AnnotationScanner;
import com.xpdustry.imperium.common.factory.ObjectFactory;
import com.xpdustry.imperium.common.lifecycle.LifecycleListener;
import com.xpdustry.imperium.common.lifecycle.LifecycleModule;
import com.xpdustry.imperium.common.lifecycle.LifecycleService;
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

    private final ObjectFactory factory =
            ObjectFactory.create(new CommonModule(), new LifecycleModule(), new DiscordModule());
    private final LifecycleService lifecycle = this.factory.get(LifecycleService.class);

    {
        for (final var object : this.factory.objects()) {
            if (object instanceof LifecycleListener listener) {
                this.lifecycle.addListener(listener);
            }
        }

        this.addListener(MindustryBridgeListener.class);
        this.addListener(PingCommand.class);
        this.addListener(ServerCommand.class);
        this.addListener(ReportListener.class);
        this.addListener(MapCommand.class);
        this.addListener(VerifyCommand.class);
        this.addListener(ModerationCommand.class);
        this.addListener(PunishmentListener.class);
        this.addListener(RoleSyncListener.class);
        this.addListener(PlayerCommand.class);
        this.addListener(AccountCommand.class);
        this.addListener(WhitelistCommand.class);
        this.addListener(RestListener.class);
        this.addListener(MapSearchCommand.class);
        this.addListener(MapSubmitCommand.class);
        this.addListener(MindustryContentListener.class);
        this.addListener(HistoryCommand.class);
        this.addListener(MetricsListener.class);

        final var scanners = new AnnotationScanner[] {
            this.factory.get(AnnotationScanner.class, "slash"),
            this.factory.get(AnnotationScanner.class, "menu"),
            this.factory.get(AnnotationScanner.class, "modal"),
        };

        this.lifecycle.load();

        for (final var listener : this.lifecycle.listeners()) {
            for (final var scanner : scanners) {
                scanner.scan(listener);
            }
        }

        for (final var scanner : scanners) {
            scanner.process();
        }

        LOGGER.info("Imperium backend loaded.");
    }

    public static void main(final String[] args) {
        new BackendLauncher();
    }

    private void addListener(final Class<? extends LifecycleListener> listener) {
        this.lifecycle.addListener(this.factory.get(listener));
    }
}
