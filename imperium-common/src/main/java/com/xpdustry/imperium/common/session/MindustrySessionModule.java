package com.xpdustry.imperium.common.session;

import com.xpdustry.imperium.common.factory.ObjectBinder;
import com.xpdustry.imperium.common.factory.ObjectModule;

public final class MindustrySessionModule implements ObjectModule {
    @Override
    public void configure(final ObjectBinder binder) {
        binder.bind(MindustrySessionService.class).toImpl(MindustrySessionServiceImpl.class);
    }
}
