package com.xpdustry.imperium.common.database;

import com.xpdustry.imperium.common.factory.ObjectBinder;
import com.xpdustry.imperium.common.factory.ObjectModule;

public final class DatabaseModule implements ObjectModule {
    @Override
    public void configure(final ObjectBinder binder) {
        binder.bind(SQLDatabase.class).toImpl(SQLDatabaseImpl.class);
    }
}
