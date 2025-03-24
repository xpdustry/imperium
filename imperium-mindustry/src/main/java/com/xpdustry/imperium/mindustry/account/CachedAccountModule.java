package com.xpdustry.imperium.mindustry.account;

import com.xpdustry.imperium.common.factory.ObjectBinder;
import com.xpdustry.imperium.common.factory.ObjectModule;

public final class CachedAccountModule implements ObjectModule {

    @Override
    public void configure(final ObjectBinder binder) {
        binder.bind(CachedAccountService.class).toImpl(CachedAccountServiceImpl.class);
    }
}
