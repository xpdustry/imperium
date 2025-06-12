package com.xpdustry.imperium.common.database;

import com.xpdustry.imperium.common.functional.ThrowingConsumer;
import com.xpdustry.imperium.common.functional.ThrowingFunction;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.stream.Stream;

public interface SQLDatabase {

    <R> R withFunctionHandle(final ThrowingFunction<Handle, R, SQLException> function);

    default void withConsumerHandle(final ThrowingConsumer<Handle, SQLException> consumer) {
        this.<Void>withFunctionHandle(handle -> {
            consumer.accept(handle);
            return null;
        });
    }

    void executeScript(final String script);

    interface Handle {

        StatementBuilder prepareStatement(final String statement);
    }

    interface StatementBuilder {

        StatementBuilder push(final String value);

        StatementBuilder push(final int value);

        StatementBuilder push(final long value);

        StatementBuilder push(final boolean value);

        StatementBuilder push(final float value);

        StatementBuilder push(final byte[] value);

        StatementBuilder push(final Instant value);

        <T> Stream<T> executeSelect(final ThrowingFunction<ResultSet, T, SQLException> mapper);

        int executeUpdate();

        boolean executeSingleUpdate();
    }
}
