package com.xpdustry.imperium.common.message;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.xpdustry.imperium.common.config.ImperiumConfig;
import com.xpdustry.imperium.common.database.SQLDatabase;
import com.xpdustry.imperium.common.lifecycle.LifecycleListener;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SQLMessageService implements MessageService, LifecycleListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SQLMessageService.class);

    private final ImperiumConfig config;
    private final SQLDatabase database;
    private final Map<Class<? extends Message>, List<? extends Subscriber<?>>> subscribers = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();

    private final Executor dispatcher = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("sql-message-service-dispatcher-", 1).factory());

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("sql-message-service-scheduler").factory());

    @Inject
    public SQLMessageService(final SQLDatabase database, final ImperiumConfig config) {
        this.database = database;
        this.config = config;
    }

    @Override
    public void onImperiumInit() {
        this.database.withConsumerHandle(ignored -> {
            this.database.executeScript(
                    """
                    CREATE TABLE IF NOT EXISTS `message_queue_sender` (
                        `name`          VARCHAR(32)         NOT NULL,
                        `last_poll`     TIMESTAMP(6)        NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        CONSTRAINT `pk_message_queue_sender`
                            PRIMARY KEY (`name`)
                    ) ENGINE = MEMORY;

                    CREATE TABLE IF NOT EXISTS `message_queue` (
                        `sender_name`   VARCHAR(32)         NOT NULL,
                        `topic`         VARCHAR(128)        NOT NULL,
                        `payload`       VARCHAR(16384)      NOT NULL,
                        `timestamp`     TIMESTAMP(6)        NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        CONSTRAINT `pk_message_queue`
                            PRIMARY KEY (`sender_name`, `topic`, `timestamp`),
                        CONSTRAINT `fk_message_queue__sender_name`
                            FOREIGN KEY (`sender_name`) REFERENCES `message_queue_sender` (`name`)
                            ON DELETE CASCADE
                    ) ENGINE = MEMORY;
                    """);
            this.cleanup();
            this.heartbeat();
        });

        // TODO Make polling rate configurable
        this.scheduler.scheduleWithFixedDelay(this::poll, 0, 200, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onImperiumExit() {
        this.scheduler.shutdown();
        try {
            if (!this.scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                this.scheduler.shutdownNow();
            }
        } catch (final InterruptedException e) {
            LOGGER.warn("Interrupted while waiting for polling tasks to finish", e);
        }
    }

    @Override
    public <M extends Message> void broadcast(final M message) {
        final var json = this.gson.toJson(message);
        this.database.withConsumerHandle(transaction -> {
            final var topic = message.getClass().getName();
            final var rows = transaction
                    .prepareStatement(
                            """
                            INSERT INTO `message_queue` (`sender_name`, `topic`, `payload`)
                            SELECT `name`, ?, ?
                            FROM `message_queue_sender`
                            WHERE
                                TIMESTAMPDIFF(SECOND, CURRENT_TIMESTAMP(6), `last_poll`) <= 30
                                AND
                                `name` != ?
                            """)
                    .push(topic)
                    .push(json.getBytes(StandardCharsets.UTF_8))
                    .push(this.config.server().name())
                    .executeUpdate();
            LOGGER.debug("Broadcast {} message to {} receivers: {}", topic, rows, json);
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public <M extends Message> void subscribe(final Class<M> type, final Subscriber<M> subscriber) {
        ((List<Subscriber<M>>) this.subscribers.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>()))
                .add(subscriber);
    }

    private void poll() {
        try {
            this.database.withConsumerHandle(transaction -> {
                this.heartbeat();

                final var messages = transaction
                        .prepareStatement(
                                """
                                SELECT `topic`, `payload`
                                FROM `message_queue`
                                WHERE `sender_name` = ?
                                ORDER BY `timestamp` DESC
                                """)
                        .push(this.config.server().name())
                        .executeSelect(this::extract)
                        .toArray(Message[]::new);
                for (final var message : messages) {
                    this.dispatch(message);
                }

                final var deleted = this.cleanup();
                if (deleted != messages.length) {
                    LOGGER.warn("Polled {} messages but deleted {}", messages.length, deleted);
                } else {
                    LOGGER.debug("Polled {} messages", messages.length);
                }
            });
        } catch (final Exception e) {
            LOGGER.error("Failed to poll messages", e);
        }
    }

    private @Nullable Message extract(final ResultSet result) throws SQLException {
        final var cname = Objects.requireNonNull(result.getString("topic"));
        final Class<?> type;
        try {
            type = Class.forName(cname, false, this.getClass().getClassLoader());
        } catch (final ClassNotFoundException e) {
            LOGGER.debug("Cannot find class {}", cname);
            return null;
        }
        if (!Message.class.isAssignableFrom(type)) {
            throw new IllegalStateException("Expected message implementing Message, got " + type);
        }

        try (final var stream = Objects.requireNonNull(result.getBinaryStream("payload"));
                final var reader = new JsonReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return this.gson.fromJson(reader, type.asSubclass(Message.class));
        } catch (final IOException e) {
            LOGGER.error("Failed to parse message {}", type, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <M extends Message> void dispatch(final M message) {
        final var subscribers = this.subscribers.get(message.getClass());
        if (subscribers == null) {
            return;
        }
        for (final var subscriber : subscribers) {
            this.dispatcher.execute(() -> {
                try {
                    ((Subscriber<M>) subscriber).onMessage(message);
                } catch (final Exception e) {
                    LOGGER.error("Failed to dispatch message {}", message, e);
                }
            });
        }
    }

    private void heartbeat() {
        this.database.withConsumerHandle(transaction -> Preconditions.checkState(transaction
                .prepareStatement(
                        """
                        INSERT INTO `message_queue_sender` (`name`)
                        VALUES (?)
                        ON DUPLICATE KEY UPDATE `last_poll` = CURRENT_TIMESTAMP(6);
                        """)
                .push(this.config.server().name())
                .executeSingleUpdate()));
    }

    private int cleanup() {
        return this.database.withFunctionHandle(transaction -> transaction
                .prepareStatement(
                        """
                        DELETE FROM `message_queue`
                        WHERE `sender_name` = ?
                        """)
                .push(this.config.server().name())
                .executeUpdate());
    }
}
