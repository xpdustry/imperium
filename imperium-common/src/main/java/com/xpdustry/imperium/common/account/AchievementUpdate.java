package com.xpdustry.imperium.common.account;

import com.xpdustry.imperium.common.message.Message;

public record AchievementUpdate(int account, Achievement achievement, boolean completed) implements Message {}
