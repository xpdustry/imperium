package com.xpdustry.imperium.common.password;

public interface PasswordHashFunction {

    default PasswordHash hash(final char[] password) {
        return hash(password, salt());
    }

    byte[] salt();

    PasswordHash hash(final char[] password, final byte[] salt);
}
