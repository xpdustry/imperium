package com.xpdustry.imperium.common.account;

import java.util.EnumSet;
import java.util.Set;

public enum Rank {
    EVERYONE,
    VERIFIED,
    OVERSEER,
    MODERATOR,
    ADMIN,
    OWNER;

    public Set<Rank> getRanksBelow() {
        return EnumSet.range(EVERYONE, this);
    }
}
