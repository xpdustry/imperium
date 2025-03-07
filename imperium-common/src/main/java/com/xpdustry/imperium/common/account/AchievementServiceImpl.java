package com.xpdustry.imperium.common.account;

import com.xpdustry.imperium.common.message.MessageService;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

final class AchievementServiceImpl implements AchievementService {

    private static final String PREFIX = "achievement_completed_";

    private final MetadataService metadata;
    private final MessageService messages;

    @Inject
    public AchievementServiceImpl(final MetadataService metadata, final MessageService messages) {
        this.metadata = metadata;
        this.messages = messages;
    }

    @Override
    public boolean selectAchievementByAccount(final int account, final Achievement achievement) {
        return this.metadata.selectMetadata(account, this.key(achievement)).isPresent();
    }

    @Override
    public Set<Achievement> selectAllAchievements(final int account) {
        return Collections.unmodifiableSet(this.metadata.selectAllMetadataByPrefix(account, PREFIX).keySet().stream()
                .filter(key -> key.startsWith(PREFIX))
                .map(key -> Achievement.valueOf(key.substring(PREFIX.length()).toUpperCase(Locale.ROOT)))
                .collect(() -> EnumSet.noneOf(Achievement.class), EnumSet::add, EnumSet::addAll));
    }

    @Override
    public boolean upsertAchievement(final int account, final Achievement achievement, final boolean completed) {
        final var updated = completed
                ? this.metadata.updateMetadata(account, this.key(achievement), "")
                : this.metadata.deleteMetadata(account, this.key(achievement));
        if (updated) {
            this.messages.broadcast(new AchievementUpdate(account, achievement, completed));
            return true;
        } else {
            return false;
        }
    }

    private String key(final Achievement achievement) {
        return PREFIX + achievement.name().toLowerCase(Locale.ROOT);
    }
}
