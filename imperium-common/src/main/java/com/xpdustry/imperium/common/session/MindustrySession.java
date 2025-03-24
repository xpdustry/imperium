package com.xpdustry.imperium.common.session;

import java.net.InetAddress;
import java.time.Instant;

public record MindustrySession(Key key, int account, Instant creation, Instant expiration, Instant lastLogin) {
    public record Key(long uuid, long usid, InetAddress address) {}
}
