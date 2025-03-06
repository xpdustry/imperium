package com.xpdustry.imperium.common.factory;

import jakarta.inject.Provider;
import org.jspecify.annotations.Nullable;

public interface ObjectBinder {

    <T> BindingBuilder<T> bind(final Class<T> type);

    interface BindingBuilder<T> {

        BindingBuilder<T> named(final @Nullable String name);

        BindingBuilder<T> visible(final boolean visible);

        void toImpl(final Class<? extends T> impl);

        void toInst(final T inst);

        void toProv(final Class<? extends Provider<? extends T>> prov);

        void toProv(final Provider<? extends T> prov);
    }
}
