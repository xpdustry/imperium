package com.xpdustry.imperium.mindustry.backport;

import com.xpdustry.distributor.api.Distributor;
import com.xpdustry.distributor.api.plugin.MindustryPlugin;
import com.xpdustry.distributor.api.plugin.PluginListener;
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit;
import com.xpdustry.imperium.mindustry.misc.MindustryExtensionsKt;
import java.lang.reflect.Field;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.gen.Groups;
import mindustry.net.Administration;
import mindustry.server.ServerControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// https://github.com/Anuken/Mindustry/issues/9422
public final class EnforceAutopauseOnLoadBackport implements PluginListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnforceAutopauseOnLoadBackport.class);

    private static final Field AUTO_PAUSE_FIELD;

    static {
        try {
            AUTO_PAUSE_FIELD = ServerControl.class.getDeclaredField("autoPaused");
            AUTO_PAUSE_FIELD.setAccessible(true);
        } catch (final ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private final MindustryPlugin plugin;

    public EnforceAutopauseOnLoadBackport(final MindustryPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginInit() {
        final var version = MindustryExtensionsKt.getMindustryVersion();
        if (version.build() < 147) {
            Distributor.get().getEventBus().subscribe(EventType.SaveLoadEvent.class, plugin, (event) -> {
                if (Administration.Config.autoPause.bool() && Groups.player.isEmpty()) {
                    this.setAutoPaused(true);
                    Vars.state.set(GameState.State.paused);
                }
            });

            // Additional fix because if you stay in-game after game-over, it re-pauses until someone joins...
            Distributor.get()
                    .getPluginScheduler()
                    .schedule(this.plugin)
                    .repeat(2, MindustryTimeUnit.SECONDS)
                    .execute((task) -> {
                        if (this.isAutoPaused() && !Groups.player.isEmpty()) {
                            this.setAutoPaused(false);
                            Vars.state.set(GameState.State.playing);
                        }
                    });
        } else {
            LOGGER.warn("The {} backport needs to be removed", this.getClass().getSimpleName());
        }
    }

    private void setAutoPaused(final boolean value) {
        try {
            AUTO_PAUSE_FIELD.setBoolean(ServerControl.instance, value);
        } catch (final ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isAutoPaused() {
        try {
            return AUTO_PAUSE_FIELD.getBoolean(ServerControl.instance);
        } catch (final ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
