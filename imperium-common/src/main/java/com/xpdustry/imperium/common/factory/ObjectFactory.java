package com.xpdustry.imperium.common.factory;

import java.util.List;
import org.jspecify.annotations.Nullable;

public interface ObjectFactory {

    static ObjectFactory create(final ObjectModule... modules) {
        return new GuiceObjectFactory(modules);
    }

    <T> List<T> collect(final Class<T> type);

    <T> T get(final Class<T> type, final @Nullable String name);

    default <T> T get(final Class<T> type) {
        return get(type, null);
    }
}
