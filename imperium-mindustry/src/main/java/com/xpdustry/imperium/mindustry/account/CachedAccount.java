package com.xpdustry.imperium.mindustry.account;

import com.xpdustry.imperium.common.account.Account;
import com.xpdustry.imperium.common.account.Achievement;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record CachedAccount(Account account, Set<Achievement> achievements, Map<String, String> metadata) {

    public CachedAccount account(final Account account) {
        return new CachedAccount(account, this.achievements, this.metadata);
    }

    public CachedAccount achievement(final Achievement achievement, final boolean completed) {
        final var achievements = EnumSet.noneOf(Achievement.class);
        achievements.addAll(this.achievements);
        if (completed) {
            achievements.add(achievement);
        } else {
            achievements.remove(achievement);
        }
        return new CachedAccount(this.account, achievements, this.metadata);
    }

    public CachedAccount metadata(final String key, final @Nullable String value) {
        final var metadata = new HashMap<>(this.metadata);
        if (value != null) {
            this.metadata.put(key, value);
        } else {
            this.metadata.remove(key);
        }
        return new CachedAccount(this.account, this.achievements, metadata);
    }
}
