package com.xpdustry.imperium.common.account;

import com.google.common.base.Preconditions;
import com.xpdustry.imperium.common.database.SQLDatabase;
import com.xpdustry.imperium.common.functional.ThrowingFunction;
import com.xpdustry.imperium.common.lifecycle.LifecycleListener;
import com.xpdustry.imperium.common.message.MessageService;
import com.xpdustry.imperium.common.password.PasswordHash;
import com.xpdustry.imperium.common.password.PasswordHashFunction;
import com.xpdustry.imperium.common.string.CharArrayString;
import com.xpdustry.imperium.common.string.MissingStringRequirementException;
import com.xpdustry.imperium.common.string.StringRequirement;
import jakarta.inject.Inject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;

final class AccountServiceImpl implements AccountService, LifecycleListener {

    private final SQLDatabase database;
    private final PasswordHashFunction function;
    private final MessageService messenger;

    @Inject
    public AccountServiceImpl(
            final SQLDatabase database, final PasswordHashFunction function, final MessageService messenger) {
        this.database = database;
        this.messenger = messenger;
        this.function = function;
    }

    @Override
    public void onImperiumInit() {
        this.database.executeScript(
                """
                CREATE TABLE IF NOT EXISTS `account` (
                    `id`                INT             NOT NULL AUTO_INCREMENT,
                    `username`          VARCHAR(32)     NOT NULL,
                    `discord`           BIGINT                   DEFAULT NULL,
                    `password_hash`     BINARY(64)      NOT NULL,
                    `password_salt`     BINARY(64)      NOT NULL,
                    `playtime`          BIGINT          NOT NULL DEFAULT 0,
                    `rank`              VARCHAR(32)     NOT NULL DEFAULT 'EVERYONE',
                    `creation`          TIMESTAMP(0)    NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
                    `legacy`            BOOLEAN         NOT NULL DEFAULT FALSE,
                    CONSTRAINT `pk_account`
                        PRIMARY KEY (`id`),
                    CONSTRAINT `uq_account__username`
                        UNIQUE (`username`),
                    CONSTRAINT `uq_account__discord`
                        UNIQUE (`discord`)
                );

                CREATE TABLE IF NOT EXISTS `legacy_account` (
                    `id`                INT         NOT NULL AUTO_INCREMENT,
                    `username_hash`     BINARY(32)  NOT NULL,
                    `password_hash`     BINARY(32)  NOT NULL,
                    `password_salt`     BINARY(16)  NOT NULL,
                    `playtime`          BIGINT      NOT NULL DEFAULT 0,
                    `rank`              VARCHAR(32) NOT NULL DEFAULT 'EVERYONE',
                    CONSTRAINT `pk_legacy_account`
                        PRIMARY KEY (`id`),
                    CONSTRAINT `uq_legacy_account__username`
                        UNIQUE (`username_hash`)
                );

                CREATE INDEX IF NOT EXISTS `ix_legacy_account__username_hash`
                    ON `legacy_account` (`username_hash`);

                CREATE TABLE IF NOT EXISTS `legacy_account_achievement` (
                    `legacy_account_id`   INT           NOT NULL,
                    `achievement`         VARCHAR(32)   NOT NULL,
                    CONSTRAINT `pk_legacy_account_achievement`
                        PRIMARY KEY (`legacy_account_id`, `achievement`),
                    CONSTRAINT `fk_legacy_account_achievement__legacy_account_id`
                        FOREIGN KEY (`legacy_account_id`)
                        REFERENCES `legacy_account`(`id`)
                        ON DELETE CASCADE
                );
                """);
    }

    @Override
    public boolean register(final String username, final char[] password) {
        this.checkUsernameRequirements(username);
        this.checkPasswordRequirements(password);
        final Integer identifier = this.database.withFunctionHandle(transaction -> {
            if (this.existsByUsername(username) || this.existsLegacyByUsername(username)) {
                return null;
            }
            final var hash = this.function.hash(password);
            return transaction
                    .prepareStatement(
                            """
                            INSERT INTO `account` (`username`, `password_hash`, `password_salt`) VALUES (?, ?, ?)
                            RETURNING `id`
                            """)
                    .push(username)
                    .push(hash.hash())
                    .push(hash.salt())
                    .executeSelect((result) -> result.getInt("id"))
                    .findFirst()
                    .orElseThrow();
        });
        if (identifier != null) {
            this.messenger.broadcast(new AccountUpdate(identifier));
        }
        return identifier != null;
    }

    @Override
    public boolean updatePassword(final int id, final char[] password) {
        this.checkPasswordRequirements(password);
        return this.database.withFunctionHandle(transaction -> {
            final var oldHash = this.selectPasswordById(id).orElse(null);
            if (oldHash == null || !oldHash.equals(this.function.hash(password, oldHash.salt()))) {
                return false;
            }
            final var newHash = this.function.hash(password);
            final var updated = transaction
                    .prepareStatement(
                            """
                            UPDATE `account`
                            SET `password_hash` = ?, `password_salt` = ?
                            WHERE `id` = ?
                            """)
                    .push(newHash.hash())
                    .push(newHash.salt())
                    .push(id)
                    .executeSingleUpdate();
            Preconditions.checkState(updated);
            return true;
        });
    }

    @Override
    public Optional<Account> selectById(final int id) {
        return this.database.withFunctionHandle(transaction -> transaction
                .prepareStatement(
                        """
                        SELECT `id`, `username`, `discord`, `games`, `playtime`, `creation`, `legacy`, `rank`
                        FROM `account`
                        WHERE `id` = ?
                        """)
                .push(id)
                .executeSelect(this::extract)
                .findFirst());
    }

    @Override
    public boolean existsById(final int id) {
        return this.database.withFunctionHandle(transaction -> transaction
                .prepareStatement(
                        """
                        SELECT COUNT(*)
                        FROM `account`
                        WHERE `id` = ?
                        """)
                .push(id)
                .executeSelect((result) -> result.getInt(1) > 0)
                .findFirst()
                .orElseThrow());
    }

    @Override
    public Optional<Account> selectByUsername(final String username) {
        return this.database.withFunctionHandle(transaction -> transaction
                .prepareStatement(
                        """
                        SELECT `id`, `username`, `discord`, `games`, `playtime`, `creation`, `legacy`, `rank`
                        FROM `account`
                        WHERE `username` = ?
                        """)
                .push(username)
                .executeSelect(this::extract)
                .findFirst());
    }

    @Override
    public boolean existsByUsername(final String username) {
        return this.database.withFunctionHandle(transaction -> transaction
                .prepareStatement(
                        """
                        SELECT COUNT(*)
                        FROM `account`
                        WHERE `username` = ?
                        """)
                .push(username)
                .executeSelect((result) -> result.getInt(1) > 0)
                .findFirst()
                .orElseThrow());
    }

    @Override
    public Optional<Account> selectByDiscord(final long discord) {
        return this.database.withFunctionHandle(transaction -> transaction
                .prepareStatement(
                        """
                        SELECT `id`, `username`, `discord`, `games`, `playtime`, `creation`, `legacy`, `rank`
                        FROM `account`
                        WHERE `discord` = ?
                        """)
                .push(discord)
                .executeSelect(this::extract)
                .findFirst());
    }

    @Override
    public boolean updateDiscord(final int id, final long discord) {
        return this.withUpdateHandle(id, transaction -> transaction
                .prepareStatement(
                        """
                        UPDATE `account`
                        SET `discord` = ?
                        WHERE `id` = ?
                        """)
                .push(discord)
                .push(id)
                .executeSingleUpdate());
    }

    @Override
    public boolean incrementPlaytime(final int id, final Duration duration) {
        return this.withUpdateHandle(id, transaction -> transaction
                .prepareStatement(
                        """
                        UPDATE `account`
                        SET `playtime` = `playtime` + ?
                        WHERE `id` = ?
                        """)
                .push(duration.getSeconds())
                .push(id)
                .executeSingleUpdate());
    }

    @Override
    public boolean updateRank(final int id, final Rank rank) {
        return this.withUpdateHandle(id, transaction -> transaction
                .prepareStatement(
                        """
                        UPDATE `account`
                        SET `rank` = ?
                        WHERE `id` = ?
                        """)
                .push(rank.name())
                .push(id)
                .executeSingleUpdate());
    }

    @Override
    public Optional<PasswordHash> selectPasswordById(final int id) {
        return this.database.withFunctionHandle(transaction -> transaction
                .prepareStatement(
                        """
                            SELECT `password_hash`, `password_salt`
                            FROM `account`
                            WHERE `id` = ?
                            """)
                .push(id)
                .executeSelect((result) ->
                        new PasswordHash(result.getBytes("password_hash"), result.getBytes("password_salt")))
                .findFirst());
    }

    @Override
    public List<StringRequirement> usernameRequirements() {
        return StringRequirement.DEFAULT_USERNAME_REQUIREMENTS;
    }

    @Override
    public List<StringRequirement> passwordRequirements() {
        return StringRequirement.DEFAULT_PASSWORD_REQUIREMENTS;
    }

    private boolean existsLegacyByUsername(final String username) {
        final byte[] hash;
        try {
            hash = MessageDigest.getInstance("SHA-256").digest(username.getBytes());
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return this.database.withFunctionHandle(transaction -> transaction
                        .prepareStatement(
                                """
                                SELECT COUNT(*) AS `count` FROM `legacy_account`
                                WHERE `username_hash` = ?
                                """)
                        .push(hash)
                        .executeSelect((result) -> result.getInt("count"))
                        .findFirst()
                        .orElseThrow())
                > 0;
    }

    private void checkUsernameRequirements(final String username) {
        final var missing = this.usernameRequirements().stream()
                .filter(requirement -> !requirement.isSatisfiedBy(username))
                .toList();
        if (!missing.isEmpty()) {
            throw new MissingStringRequirementException("Missing username requirements", missing);
        }
    }

    private void checkPasswordRequirements(final char[] password) {
        try (final var wrapped = new CharArrayString(password)) {
            final var missing = this.passwordRequirements().stream()
                    .filter(requirement -> !requirement.isSatisfiedBy(wrapped))
                    .toList();
            if (!missing.isEmpty()) {
                throw new MissingStringRequirementException("Missing password requirements", missing);
            }
        }
    }

    private Account extract(final ResultSet result) throws SQLException {
        // TODO This is goofy, replace by primitive
        Long discord = result.getLong("discord");
        if (result.wasNull()) {
            discord = null;
        }
        return new Account(
                result.getInt("id"),
                result.getString("username"),
                Duration.ofSeconds(result.getLong("playtime")),
                result.getTimestamp("creation").toInstant(),
                result.getBoolean("legacy"),
                Rank.valueOf(result.getString("rank")),
                discord);
    }

    private boolean withUpdateHandle(
            final int account, final ThrowingFunction<SQLDatabase.Handle, Boolean, SQLException> function) {
        final var updated = this.database.withFunctionHandle(function);
        if (updated) {
            this.messenger.broadcast(new AccountUpdate(account));
        }
        return updated;
    }
}
