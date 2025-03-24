package com.xpdustry.imperium.common.password;

import com.xpdustry.imperium.common.factory.ObjectBinder;
import com.xpdustry.imperium.common.factory.ObjectModule;

public final class PasswordModule implements ObjectModule {
    @Override
    public void configure(final ObjectBinder binder) {
        binder.bind(PasswordHashFunction.class).toImpl(ImperiumHashFunctionV1.class);
    }
}
