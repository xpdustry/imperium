package com.xpdustry.imperium.common.account;

import com.xpdustry.imperium.common.config.ImperiumConfig;
import com.xpdustry.imperium.common.database.SQLDatabase;
import com.xpdustry.imperium.common.lifecycle.LifecycleListener;
import com.xpdustry.imperium.common.message.MessageService;
import com.xpdustry.imperium.common.password.PasswordHashFunction;
import jakarta.inject.Inject;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

final class MindustrySessionServiceImpl implements MindustrySessionService, LifecycleListener {

    private final SQLDatabase database;
    private final AccountService accounts;
    private final PasswordHashFunction function;
    private final MessageService messenger;
    private final ImperiumConfig config;

    @Inject
    public MindustrySessionServiceImpl(
            final SQLDatabase database,
            final AccountService accounts,
            final PasswordHashFunction function,
            final MessageService messenger,
            final ImperiumConfig config) {
        this.database = database;
        this.accounts = accounts;
        this.function = function;
        this.messenger = messenger;
        this.config = config;
    }

    @Override
    public void onImperiumInit() {
        this.database.executeScript(
                """
                CREATE TABLE IF NOT EXISTS `account_mindustry_session` (
                    `account_id`    INT             NOT NULL,
                    `uuid`          BIGINT          NOT NULL,
                    `usid`          BIGINT          NOT NULL,
                    `address`       VARBINARY(16)   NOT NULL,
                    `server`        VARCHAR(32)     NOT NULL,
                    `createdAt`     TIMESTAMP(0)    NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
                    `lastLogin`     TIMESTAMP(0)    NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
                    `expiresAt`     TIMESTAMP(0)    NOT NULL DEFAULT TIMESTAMPADD(DAY, 7, CURRENT_TIMESTAMP(0)),
                    CONSTRAINT `pk_account_session`
                        PRIMARY KEY (`account_id`, `uuid`, `usid`, `address`),
                    CONSTRAINT `fk_account_session__account_id`
                        FOREIGN KEY (`account_id`)
                        REFERENCES `account`(`id`)
                        ON DELETE CASCADE
                );
                """);
    }

    @Override
    public Optional<MindustrySession> selectByKey(final MindustrySession.Key key) {
        return this.database.withFunctionHandle(transaction -> transaction
                .prepareStatement(
                        """
                        SELECT `account_id`, `uuid`, `usid`, `address`, `server`, `createdAt`, `expiresAt`, `lastLogin`
                        FROM `account_mindustry_session`
                        WHERE `uuid` = ? AND `usid` = ? AND `address` = ?
                        """)
                .push(key.uuid())
                .push(key.usid())
                .push(key.address().getAddress())
                .executeSelect(this::extract)
                .findFirst()
                .filter(this::validate));
    }

    @Override
    public List<MindustrySession> selectAllByAccount(final int account) {
        return this.database.withFunctionHandle(transaction -> transaction
                .prepareStatement(
                        """
                        SELECT `account_id`, `uuid`, `usid`, `address`, `server`, `createdAt`, `expiresAt`, `lastLogin`
                        FROM `account_mindustry_session`
                        WHERE `account_id` = ?
                        """)
                .push(account)
                .executeSelect(this::extract)
                .filter(this::validate)
                .toList());
    }

    @Override
    public boolean login(final MindustrySession.Key key, final String username, final char[] password) {
        final var session = this.selectByKey(key);
        if (session.isPresent()) {
            return false;
        }

        final var account = this.accounts.selectByUsername(username);
        if (account.isEmpty()) {
            return false;
        }

        final var hash1 = this.accounts.selectPasswordById(account.get().id()).orElseThrow();
        final var hash2 = this.function.hash(password, hash1.salt());
        if (!hash1.equals(hash2)) {
            return false;
        }

        this.database.withFunctionHandle(transaction -> transaction
                .prepareStatement(
                        """
                        INSERT INTO `account_mindustry_session` (`account_id`, `uuid`, `usid`, `address`, `server`)
                        VALUES (?, ?, ?, ?, ?)
                        """)
                .push(account.get().id())
                .push(key.uuid())
                .push(key.usid())
                .push(key.address().getAddress())
                .push(config.server().name())
                .executeSingleUpdate());
        this.messenger.broadcast(new MindustrySessionUpdate(key, MindustrySessionUpdate.Type.CREATE));
        return true;
    }

    @Override
    public boolean logout(final MindustrySession.Key key, final boolean all) {
        final var session = this.selectByKey(key);
        if (session.isEmpty()) {
            return false;
        }
        final var updated = all ? this.deleteAllByAccount(session.get().account()) : this.deleteByKey(key);
        if (updated) {
            this.messenger.broadcast(new MindustrySessionUpdate(key, MindustrySessionUpdate.Type.DELETE));
        }
        return updated;
    }

    private boolean deleteAllByAccount(final int account) {
        return this.database.withFunctionHandle(transaction -> transaction
                        .prepareStatement(
                                """
                        DELETE FROM `account_mindustry_session`
                        WHERE `account_id` = ?
                        """)
                        .push(account)
                        .executeUpdate()
                > 0);
    }

    private boolean deleteByKey(final MindustrySession.Key key) {
        return this.database.withFunctionHandle(transaction -> transaction
                .prepareStatement(
                        """
                        DELETE FROM `account_mindustry_session`
                        WHERE `uuid` = ? AND `usid` = ? AND `address` = ?
                        """)
                .push(key.uuid())
                .push(key.usid())
                .push(key.address().getAddress())
                .executeSingleUpdate());
    }

    private MindustrySession extract(final ResultSet result) throws SQLException {
        final InetAddress address;
        try {
            address = InetAddress.getByAddress(result.getBytes("address"));
        } catch (final UnknownHostException e) {
            throw new RuntimeException(e);
        }
        return new MindustrySession(
                new MindustrySession.Key(result.getLong("uuid"), result.getLong("usid"), address),
                result.getInt("account"),
                result.getTimestamp("createdAt").toInstant(),
                result.getTimestamp("expiresAt").toInstant(),
                result.getTimestamp("lastLogin").toInstant());
    }

    private boolean validate(final MindustrySession session) {
        final var now = Instant.now();
        if (session.expiresAt().isAfter(now)
                || session.lastLogin().plus(Duration.ofDays(7)).isAfter(now)) {
            return true;
        } else {
            if (this.deleteByKey(session.key())) {
                this.messenger.broadcast(new MindustrySessionUpdate(session.key(), MindustrySessionUpdate.Type.DELETE));
            }
            return false;
        }
    }
}
