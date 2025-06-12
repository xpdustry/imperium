package com.xpdustry.imperium.common.factory;

import com.google.inject.*;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Names;
import com.google.inject.spi.ProvisionListener;
import jakarta.inject.Provider;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;

final class GuiceObjectFactory implements ObjectFactory, ProvisionListener {

    private final Injector injector;
    private final List<Object> provisioned = new CopyOnWriteArrayList<>();

    @SuppressWarnings("UnusedAssignment")
    private boolean initialized = false;

    public GuiceObjectFactory(final ObjectModule... modules) {
        this.injector = Guice.createInjector(Stage.PRODUCTION, new InternalObjectFactoryModule(List.of(modules)));
        this.initialized = true;
    }

    @Override
    public <T> T get(final Class<T> type, final String name) {
        return this.injector.getInstance(name == null ? Key.get(type) : Key.get(type, Names.named(name)));
    }

    @Override
    public <T> List<T> collect(final Class<T> type) {
        if (!this.initialized) {
            throw new IllegalStateException("You cannot collect objects while initializing");
        }
        return this.provisioned.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    @Override
    public <T> void onProvision(final ProvisionInvocation<T> provision) {
        if (!this.initialized) {
            this.provisioned.add(provision.provision());
        }
    }

    private final class InternalObjectFactoryModule extends AbstractModule {

        private final List<ObjectModule> modules;

        private InternalObjectFactoryModule(final List<ObjectModule> modules) {
            this.modules = modules;
        }

        @Override
        protected void configure() {
            this.binder().skipSources(InternalObjectFactoryModule.class, ObjectModuleAdapter.class);
            this.binder().bindListener(Matchers.any(), GuiceObjectFactory.this);
            this.binder().disableCircularProxies();
            this.binder().bind(ObjectFactory.class).toInstance(GuiceObjectFactory.this);
            for (final var module : this.modules) {
                this.install(new ObjectModuleAdapter(module));
            }
        }
    }

    private static final class ObjectModuleAdapter extends PrivateModule {

        private final ObjectModule module;

        private ObjectModuleAdapter(final ObjectModule module) {
            this.module = module;
        }

        @Override
        protected void configure() {
            this.module.configure(new GuiceObjectBinder(this.binder()));
        }
    }

    private record GuiceObjectBinder(PrivateBinder binder) implements ObjectBinder {

        @Override
        public <T> BindingBuilder<T> bind(final Class<T> type) {
            return new GuiceBindingBuilder<>(type);
        }

        private final class GuiceBindingBuilder<T> implements BindingBuilder<T> {

            private boolean visible = true;
            private Key<T> key;

            private GuiceBindingBuilder(final Class<T> type) {
                this.key = Key.get(type);
            }

            @Override
            public BindingBuilder<T> named(final @Nullable String name) {
                this.key = name == null ? key.withoutAttributes() : key.withAnnotation(Names.named(name));
                return this;
            }

            @Override
            public BindingBuilder<T> visible(final boolean visible) {
                this.visible = visible;
                return this;
            }

            @Override
            public void toImpl(final Class<? extends T> type) {
                GuiceObjectBinder.this.binder.bind(this.key).to(type).asEagerSingleton();
                if (this.visible) {
                    GuiceObjectBinder.this.binder.expose(this.key);
                }
            }

            @Override
            public void toInst(final T instance) {
                GuiceObjectBinder.this.binder.bind(this.key).toInstance(instance);
                if (this.visible) {
                    GuiceObjectBinder.this.binder.expose(this.key);
                }
            }

            @Override
            public void toProv(final Class<? extends Provider<? extends T>> prov) {
                GuiceObjectBinder.this.binder.bind(this.key).toProvider(prov).asEagerSingleton();
                if (this.visible) {
                    GuiceObjectBinder.this.binder.expose(this.key);
                }
            }

            @Override
            public void toProv(final Provider<? extends T> prov) {
                GuiceObjectBinder.this.binder.bind(this.key).toProvider(prov).asEagerSingleton();
                if (this.visible) {
                    GuiceObjectBinder.this.binder.expose(this.key);
                }
            }
        }
    }
}
