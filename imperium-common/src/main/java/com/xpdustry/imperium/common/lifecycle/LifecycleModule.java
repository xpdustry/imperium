package com.xpdustry.imperium.common.lifecycle;

import com.xpdustry.imperium.common.factory.ObjectBinder;
import com.xpdustry.imperium.common.factory.ObjectModule;

public final class LifecycleModule implements ObjectModule {

    @Override
    public void configure(final ObjectBinder binder) {
        binder.bind(LifecycleService.class).toImpl(LifecycleServiceImpl.class);
    }
}
