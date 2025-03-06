package com.xpdustry.imperium.common.database;

import com.xpdustry.imperium.common.config.DatabaseConfig;
import com.xpdustry.imperium.common.config.ImperiumConfig;
import com.xpdustry.imperium.common.functional.ThrowingFunction;
import com.xpdustry.imperium.common.lifecycle.LifecycleListener;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("preview")
final class SQLDatabaseImpl implements SQLDatabase, LifecycleListener {

    private static final ScopedValue<HandleImpl> HANDLE = ScopedValue.newInstance();

    private final ImperiumConfig config;
    private final Path directory;
    private @Nullable HikariDataSource source = null;

    @Inject
    public SQLDatabaseImpl(final ImperiumConfig config, final @Named("directory") Path directory) {
        this.config = config;
        this.directory = directory;
    }

    @Override
    public void onImperiumInit() {
        final var hikari = new HikariConfig();
        hikari.setPoolName("imperium-sql-pool");
        hikari.setMaximumPoolSize(8);
        hikari.setMinimumIdle(2);
        hikari.addDataSourceProperty("createDatabaseIfNotExist", "true");

        switch (this.config.database()) {
            case DatabaseConfig.MariaDB maria -> {
                hikari.setDriverClassName("org.mariadb.jdbc.Driver");
                hikari.setJdbcUrl("jdbc:mariadb://" + maria.host() + ":" + maria.port() + "/" + maria.database());
                hikari.setUsername(maria.username());
                hikari.setPassword(maria.password().getValue());
            }

            case DatabaseConfig.H2 ignored -> {
                hikari.setDriverClassName("org.h2.Driver");
                hikari.setJdbcUrl("jdbc:h2:file:"
                        + this.directory.resolve("database.h2").toAbsolutePath() + ";MODE=MYSQL;AUTO_SERVER=TRUE");
            }

            default -> throw new IllegalStateException("Unexpected value: " + this.config);
        }

        this.source = new HikariDataSource(hikari);
    }

    @Override
    public void onImperiumExit() {
        Objects.requireNonNull(this.source).close();
    }

    @SuppressWarnings("SqlSourceToSinkFlow")
    @Override
    public void executeScript(final String script) {
        this.withFunctionHandle(handle -> {
            final var connection = ((HandleImpl) handle).connection;
            try (final var statement = connection.createStatement()) {
                for (var line : script.split(";", -1)) {
                    line = line.trim();
                    if (line.isBlank() || line.startsWith("--")) continue;
                    statement.addBatch(line);
                }
                statement.executeBatch();
            } catch (final SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    @Override
    public <R> R withFunctionHandle(final ThrowingFunction<Handle, R, SQLException> function) {
        Objects.requireNonNull(this.source);

        if (HANDLE.isBound()) {
            try {
                return function.apply(HANDLE.get());
            } catch (final SQLException e) {
                throw new RuntimeException(e);
            }
        }

        try (final var connection = this.source.getConnection()) {
            return ScopedValue.callWhere(HANDLE, new HandleImpl(connection), () -> {
                final var handle = HANDLE.get();
                try {
                    handle.connection.setAutoCommit(false);
                    handle.connection.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
                    final var result = function.apply(handle);
                    handle.connection.commit();
                    return result;
                } catch (final SQLException e) {
                    handle.connection.rollback();
                    throw new RuntimeException(e);
                } finally {
                    handle.connection.close();
                }
            });
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private record HandleImpl(Connection connection) implements Handle {

        @SuppressWarnings("SqlSourceToSinkFlow")
        @Override
        public StatementBuilder prepareStatement(final String statement) {
            try {
                return new StatementBuilderImpl(statement, connection.prepareStatement(statement));
            } catch (final SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final class StatementBuilderImpl implements SQLDatabase.StatementBuilder {

        // MySQL/MariaDB handles this as a delete + insert in case ofa duplicate, 2 operations therefore 2 updates
        private static final Pattern INSERT_WITH_UPDATE_PATTERN = Pattern.compile(
                "^\\s*INSERT\\s+(.|\\s)*\\s+ON\\s+DUPLICATE\\s+KEY\\s+UPDATE\\s+", Pattern.CASE_INSENSITIVE);

        private final String raw;
        private final PreparedStatement statement;
        private int index = 1;

        private StatementBuilderImpl(final String raw, final PreparedStatement statement) {
            this.raw = raw;
            this.statement = statement;
        }

        @Override
        public StatementBuilder push(final String value) {
            try {
                this.statement.setString(this.index, value);
                this.index++;
                return this;
            } catch (final SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public StatementBuilder push(final int value) {
            try {
                this.statement.setInt(this.index, value);
                this.index++;
                return this;
            } catch (final SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public StatementBuilder push(final long value) {
            try {
                this.statement.setLong(this.index, value);
                this.index++;
                return this;
            } catch (final SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public StatementBuilder push(final boolean value) {
            try {
                this.statement.setBoolean(this.index, value);
                this.index++;
                return this;
            } catch (final SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public StatementBuilder push(final float value) {
            try {
                this.statement.setFloat(this.index, value);
                this.index++;
                return this;
            } catch (final SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public StatementBuilder push(final byte[] value) {
            try {
                this.statement.setBytes(this.index, value);
                this.index++;
                return this;
            } catch (final SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public StatementBuilder push(final Instant value) {
            try {
                this.statement.setTimestamp(this.index, Timestamp.from(value));
                this.index++;
                return this;
            } catch (final SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public <T> Stream<T> executeSelect(final ThrowingFunction<ResultSet, T, SQLException> mapper) {
            this.ensureIsNotClosed();
            try {
                final var result = statement.executeQuery();
                final var list = new ArrayList<T>();
                while (result.next()) {
                    list.add(mapper.apply(result));
                }
                return list.stream();
            } catch (final SQLException e) {
                throw new RuntimeException(e);
            } finally {
                this.tryClose();
            }
        }

        @Override
        public int executeUpdate() {
            this.ensureIsNotClosed();
            try {
                int result = this.statement.executeUpdate();
                if (result == 2 && INSERT_WITH_UPDATE_PATTERN.matcher(this.raw).find()) {
                    result--;
                }
                return result;
            } catch (final SQLException e) {
                throw new RuntimeException(e);
            } finally {
                this.tryClose();
            }
        }

        @Override
        public boolean executeSingleUpdate() {
            final var result = this.executeUpdate();
            return switch (result) {
                case 0 -> false;
                case 1 -> true;
                default -> throw new IllegalStateException("Multiple rows updated, expected 0 or 1, got " + result);
            };
        }

        private void ensureIsNotClosed() {
            final boolean closed;
            try {
                closed = this.statement.isClosed();
            } catch (final SQLException e) {
                throw new RuntimeException(e);
            }
            if (closed) {
                throw new IllegalStateException("The statement has already been consumed.");
            }
        }

        private void tryClose() {
            try {
                this.statement.close();
            } catch (final SQLException e) {
                // TODO Log the close failure
            }
        }
    }
}
