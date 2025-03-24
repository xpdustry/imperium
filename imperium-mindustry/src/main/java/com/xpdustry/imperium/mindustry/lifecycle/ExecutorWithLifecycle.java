package com.xpdustry.imperium.mindustry.lifecycle;

import com.xpdustry.imperium.common.lifecycle.LifecycleListener;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExecutorWithLifecycle implements Executor, LifecycleListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorWithLifecycle.class);

    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("mindustry-executor-worker-", 1).factory());

    @Override
    public void execute(final @NotNull Runnable command) {
        this.executor.execute(command);
    }

    @Override
    public void onImperiumExit() {
        this.executor.shutdown();
        try {
            if (!this.executor.awaitTermination(10, TimeUnit.SECONDS)) {
                this.executor.shutdownNow();
            }
        } catch (final Exception e) {
            LOGGER.error("Failed to shutdown executor", e);
        }
    }
}
