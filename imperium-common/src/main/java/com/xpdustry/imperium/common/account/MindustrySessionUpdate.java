package com.xpdustry.imperium.common.account;

import com.xpdustry.imperium.common.message.Message;

public record MindustrySessionUpdate(MindustrySession.Key player, Type type) implements Message {
    public enum Type {
        CREATE,
        UPDATE,
        DELETE,
    }
}
