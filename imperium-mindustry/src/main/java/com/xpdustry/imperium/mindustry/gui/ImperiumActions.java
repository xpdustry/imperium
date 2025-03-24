package com.xpdustry.imperium.mindustry.gui;

import arc.Core;
import com.xpdustry.distributor.api.component.Component;
import com.xpdustry.distributor.api.gui.Action;
import com.xpdustry.distributor.api.gui.Window;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.slf4j.LoggerFactory;

public final class ImperiumActions {

    private ImperiumActions() {}

    public static Action delegate(final Executor executor, final Function<Window, Action> delegate) {
        return window -> executor.execute(() -> {
            try {
                final var result = delegate.apply(window);
                Core.app.post(() -> result.act(window));
            } catch (final Exception e) {
                LoggerFactory.getLogger(ImperiumActions.class)
                        .error("An unexpected error occurred in a coroutine action", e);
                Action.hideAll().act(window);
            }
        });
    }

    public static Action announce(final Component message) {
        return Action.audience(audience -> audience.sendAnnouncement(message));
    }
}
