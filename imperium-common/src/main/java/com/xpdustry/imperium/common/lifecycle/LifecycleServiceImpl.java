package com.xpdustry.imperium.common.lifecycle;

import com.xpdustry.imperium.common.factory.ObjectFactory;
import com.xpdustry.imperium.common.webhook.WebhookChannel;
import com.xpdustry.imperium.common.webhook.WebhookMessage;
import com.xpdustry.imperium.common.webhook.WebhookMessageSender;
import jakarta.inject.Inject;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.SequencedCollection;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class LifecycleServiceImpl implements LifecycleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LifecycleServiceImpl.class);

    private final Deque<LifecycleListener> toLoad = new ArrayDeque<>();
    private final Deque<LifecycleListener> loaded = new ArrayDeque<>();
    private final AtomicBoolean exited = new AtomicBoolean(false);

    private final PlatformExitService exit;
    private final ObjectFactory factory;
    private final WebhookMessageSender webhook;

    @Inject
    public LifecycleServiceImpl(
            final PlatformExitService exit, final ObjectFactory factory, final WebhookMessageSender webhook) {
        this.exit = exit;
        this.factory = factory;
        this.webhook = webhook;
    }

    @Override
    public void addListener(final LifecycleListener listener) {
        this.toLoad.add(listener);
    }

    @Override
    public void addListener(final Class<? extends LifecycleListener> listener) {
        this.toLoad.add(this.factory.get(listener));
    }

    @Override
    public void load() {
        for (final var listener : this.factory.collect(LifecycleListener.class).reversed()) {
            this.toLoad.addFirst(listener);
        }
        try {
            while (!this.toLoad.isEmpty()) {
                final var listener = this.toLoad.removeFirst();
                listener.onImperiumInit();
                this.loaded.addFirst(listener);
            }
            this.webhook.sendBlocking(
                    WebhookChannel.CONSOLE, new WebhookMessage("Imperium has been successfully loaded."));
        } catch (final Exception exception) {
            this.exit(PlatformExitCode.FAILURE);
            throw exception;
        }
    }

    @Override
    public void exit(PlatformExitCode code) {
        if (this.exited.getAndSet(true)) {
            return;
        }

        for (final var listener : this.loaded) {
            try {
                listener.onImperiumExit();
            } catch (final Exception e) {
                LOGGER.error("Failed to exit listener {}", listener.getClass().getSimpleName(), e);
                if (code == PlatformExitCode.SUCCESS) {
                    code = PlatformExitCode.FAILURE;
                }
            }
        }

        try {
            this.webhook.sendBlocking(
                    WebhookChannel.CONSOLE, new WebhookMessage("The server is exiting with %s code.".formatted(code)));
        } catch (final Exception e) {
            LOGGER.error("Failed to send webhook message", e);
        }

        this.exit.exit(code);
    }

    @Override
    public SequencedCollection<LifecycleListener> listeners() {
        return this.loaded;
    }
}
