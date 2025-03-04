package com.xpdustry.imperium.common.factory;

import com.google.common.base.Preconditions;
import com.google.inject.*;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Names;
import com.google.inject.spi.ProvisionListener;
import jakarta.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class GuiceObjectFactory implements ObjectFactory {

    private final List<ObjectModule> modules;
    private List<Object> objects = List.of();
    private @Nullable Injector injector = null;

    public GuiceObjectFactory(final ObjectModule... modules) {
        this.modules = List.of(modules);
    }

    @Override
    public <T> T get(final Class<T> clazz, final String name) {
        Preconditions.checkState(this.injector != null, "Objects are not linked yet");
        final var key = name == null ? Key.get(clazz) : Key.get(clazz, Names.named(name));
        return this.injector.getInstance(key);
    }

    @Override
    public void initialize() throws ObjectFactoryInitializationException {
        Preconditions.checkState(this.injector == null, "The factory is already initialized");
        final var collector = new ResolvedObjectCollector();
        try {
            this.injector = Guice.createInjector(Stage.PRODUCTION, new ModuleProxy(this.modules, collector));
        } catch (final CreationException e) {
            throw new ObjectFactoryInitializationException(e);
        } finally {
            this.objects = List.copyOf(collector.resolved);
            collector.close();
        }
    }

    @Override
    public List<Object> objects() {
        return this.objects;
    }

    private static final class ResolvedObjectCollector implements ProvisionListener {

        private final List<Object> resolved = new ArrayList<>();
        private boolean closed = false;

        @Override
        public <T> void onProvision(final ProvisionInvocation<T> provision) {
            if (!this.closed) {
                this.resolved.add(provision.provision());
            }
        }

        public void close() {
            this.resolved.clear();
            this.closed = true;
        }
    }

    private record ModuleProxy(List<ObjectModule> modules, ProvisionListener listener) implements Module {

        @Override
        public void configure(final Binder binder) {
            binder.disableCircularProxies();
            binder.bindListener(Matchers.any(), this.listener);
            final var guice = new GuiceObjectBinder(binder);
            for (final var module : this.modules) {
                module.configure(guice);
            }
        }
    }

    private record GuiceObjectBinder(Binder binder) implements ObjectBinder {

        @Override
        public <T> BindingBuilder<T> bind(final Class<T> type) {
            return new GuiceBindingBuilder<>(this.binder, type);
        }

        private static final class GuiceBindingBuilder<T> implements ObjectBinder.BindingBuilder<T> {

            private final Binder binder;
            private final Class<T> type;
            private @Nullable String name;

            private GuiceBindingBuilder(final Binder binder, final Class<T> type) {
                this.binder = binder;
                this.type = type;
            }

            @Override
            public void toImpl(final Class<? extends T> clazz) {
                this.builder().to(clazz).asEagerSingleton();
            }

            @Override
            public void toInst(final T instance) {
                this.builder().toInstance(instance);
            }

            @Override
            public void toProv(final Class<? extends Provider<? extends T>> clazz) {
                this.builder().toProvider(clazz).asEagerSingleton();
            }

            @Override
            public void toProv(final Provider<? extends T> provider) {
                this.builder().toProvider(provider).asEagerSingleton();
            }

            @Override
            public ObjectBinder.BindingBuilder<T> named(final String name) {
                this.name = name;
                return this;
            }

            private LinkedBindingBuilder<T> builder() {
                final var builder = this.binder.bind(this.type);
                return this.name == null ? builder : builder.annotatedWith(Names.named(this.name));
            }
        }
    }
}
