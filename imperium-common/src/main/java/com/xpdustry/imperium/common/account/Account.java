package com.xpdustry.imperium.common.account;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record Account(
        int id,
        String username,
        Duration playtime,
        Instant creation,
        boolean legacy,
        Rank rank,
        @Nullable Long discord) {}
