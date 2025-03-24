package com.xpdustry.imperium.common.message;

import com.xpdustry.imperium.common.factory.ObjectBinder;
import com.xpdustry.imperium.common.factory.ObjectModule;

public final class MessageModule implements ObjectModule {
    @Override
    public void configure(final ObjectBinder binder) {
        binder.bind(MessageService.class).toImpl(SQLMessageService.class);
    }
}
