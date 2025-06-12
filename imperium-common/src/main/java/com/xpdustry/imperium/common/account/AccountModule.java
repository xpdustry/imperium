package com.xpdustry.imperium.common.account;

import com.xpdustry.imperium.common.factory.ObjectBinder;
import com.xpdustry.imperium.common.factory.ObjectModule;

public final class AccountModule implements ObjectModule {

    @Override
    public void configure(final ObjectBinder binder) {
        binder.bind(AccountService.class).toImpl(AccountServiceImpl.class);
        binder.bind(AchievementService.class).toImpl(AchievementServiceImpl.class);
        binder.bind(MetadataService.class).toImpl(MetadataServiceImpl.class);
    }
}
