package com.xpdustry.imperium.common.password;

import java.security.SecureRandom;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

public final class ImperiumHashFunctionV1 implements PasswordHashFunction {

    private final SecureRandom random = new SecureRandom();

    @Override
    public PasswordHash hash(final char[] password, final byte[] salt) {
        final var generator = new Argon2BytesGenerator();
        generator.init(new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withMemoryAsKB(64 * 1024) // Insanity
                .withIterations(3)
                .withParallelism(2)
                .withSalt(salt)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .build());
        final var hash = new byte[64];
        generator.generateBytes(password, hash, 0, hash.length);
        return new PasswordHash(hash, salt);
    }

    @Override
    public byte[] salt() {
        final var salt = new byte[64];
        this.random.nextBytes(salt);
        return salt;
    }
}
