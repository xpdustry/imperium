package com.xpdustry.imperium.common.factory;

import java.util.List;
import org.jspecify.annotations.Nullable;

public interface ObjectFactory {

    static ObjectFactory create(final ObjectModule... modules) {
        return new GuavaObjectFactory(modules);
    }

    void initialize() throws ObjectFactoryInitializationException;

    List<Object> objects();

    <T> T get(final Class<T> type, final @Nullable String name);

    default <T> T get(final Class<T> type) {
        return get(type, null);
    }
}
