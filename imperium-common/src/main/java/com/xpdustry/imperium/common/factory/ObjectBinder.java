package com.xpdustry.imperium.common.factory;

import jakarta.inject.Provider;

public interface ObjectBinder {

    <T> BindingBuilder<T> bind(final Class<T> type);

    interface BindingBuilder<T> {

        BindingBuilder<T> named(final String name);

        void toImpl(final Class<? extends T> type);

        void toInst(final T instance);

        void toProv(final Class<? extends Provider<? extends T>> type);

        void toProv(final Provider<? extends T> provider);
    }
}
