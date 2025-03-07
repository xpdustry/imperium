package com.xpdustry.imperium.common.account;

import java.net.InetAddress;
import java.time.Instant;

public record MindustrySession(Key key, int account, Instant createdAt, Instant expiresAt, Instant lastLogin) {
    public record Key(long uuid, long usid, InetAddress address) {}
}
