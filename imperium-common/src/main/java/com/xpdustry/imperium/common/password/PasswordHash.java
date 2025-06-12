package com.xpdustry.imperium.common.password;

import java.util.Arrays;
import java.util.Objects;

public record PasswordHash(byte[] hash, byte[] salt) {

    public PasswordHash(final byte[] hash, final byte[] salt) {
        this.hash = hash.clone();
        this.salt = salt.clone();
    }

    @Override
    public byte[] hash() {
        return hash.clone();
    }

    @Override
    public byte[] salt() {
        return salt.clone();
    }

    @Override
    public boolean equals(final Object other) {
        return (other instanceof PasswordHash cast)
                && timeConstantEquals(this.hash, cast.hash)
                && timeConstantEquals(this.salt, cast.salt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(hash), Arrays.hashCode(salt));
    }

    @Override
    public String toString() {
        final var builder = new StringBuilder("PasswordHash{hash=");
        appendHex(builder, hash);
        builder.append(", salt=");
        appendHex(builder, salt);
        builder.append('}');
        return builder.toString();
    }

    private static void appendHex(final StringBuilder builder, final byte[] bytes) {
        builder.append("0x");
        for (final var b : bytes) builder.append(String.format("%02X", b));
    }

    private static boolean timeConstantEquals(final byte[] a, final byte[] b) {
        int diff = a.length ^ b.length;
        int i = 0;
        while (i < a.length && i < b.length) {
            diff |= (a[i] & 0xFF) ^ (b[i] & 0xFF);
            i++;
        }
        return diff == 0;
    }
}
