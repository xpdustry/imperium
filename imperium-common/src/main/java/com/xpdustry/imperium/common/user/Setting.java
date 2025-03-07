package com.xpdustry.imperium.common.user;

import com.xpdustry.imperium.common.account.Achievement;

public enum Setting {
    SHOW_WELCOME_MESSAGE(true),
    RESOURCE_HUD(true),
    REMEMBER_LOGIN(true),
    DOUBLE_TAP_TILE_LOG(true),
    ANTI_BAN_EVADE(false),
    CHAT_TRANSLATOR(true, true),
    AUTOMATIC_LANGUAGE_DETECTION(true),
    UNDERCOVER(false),
    RAINBOW_NAME(false, false, Achievement.SUPPORTER);

    private final boolean def;
    private final boolean deprecated;
    private final Achievement achievement;

    Setting(final boolean def, boolean deprecated, Achievement achievement) {
        this.def = def;
        this.deprecated = deprecated;
        this.achievement = achievement;
    }

    Setting(final boolean def) {
        this(def, false, null);
    }

    Setting(final boolean def, boolean deprecated) {
        this(def, deprecated, null);
    }

    public boolean def() {
        return def;
    }

    public boolean deprecated() {
        return deprecated;
    }

    public Achievement achievement() {
        return achievement;
    }
}
