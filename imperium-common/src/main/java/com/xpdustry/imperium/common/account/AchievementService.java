package com.xpdustry.imperium.common.account;

import java.util.Set;

public interface AchievementService {

    boolean selectAchievementByAccount(final int account, final Achievement achievement);

    Set<Achievement> selectAllAchievements(final int account);

    boolean upsertAchievement(final int account, final Achievement achievement, final boolean completed);
}
