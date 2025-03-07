package com.xpdustry.imperium.common.account;

import com.xpdustry.imperium.common.message.Message;
import org.jspecify.annotations.Nullable;

public record MetadataUpdate(int account, String key, @Nullable String value) implements Message {}
