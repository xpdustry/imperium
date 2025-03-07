package com.xpdustry.imperium.common.account;

import com.xpdustry.imperium.common.database.SQLDatabase;
import com.xpdustry.imperium.common.lifecycle.LifecycleListener;
import com.xpdustry.imperium.common.message.MessageService;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

final class MetadataServiceImpl implements MetadataService, LifecycleListener {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9_]+$");

    private final SQLDatabase database;
    private final MessageService messenger;

    // The unused AccountService makes sure Metadata is initialized after it
    @Inject
    public MetadataServiceImpl(
            final SQLDatabase database, final MessageService messenger, final AccountService ignored) {
        this.database = database;
        this.messenger = messenger;
    }

    @Override
    public void onImperiumInit() {
        this.database.executeScript(
                """
                CREATE TABLE IF NOT EXISTS `account_metadata` (
                    `account_id`    INT         NOT NULL,
                    `key`           VARCHAR(64) NOT NULL,
                    `value`         TEXT        NOT NULL,
                    CONSTRAINT `pk_account_metadata`
                        PRIMARY KEY (`account_id`, `key`),
                    CONSTRAINT `fk_account_metadata__account_id`
                        FOREIGN KEY (`account_id`)
                        REFERENCES `account`(`id`)
                        ON DELETE CASCADE,
                    CONSTRAINT `ch_account_metadata__key`
                        CHECK (`key` REGEXP '^[a-z0-9_]+$')
                );
                """);
    }

    @Override
    public Optional<String> selectMetadata(final int account, final String key) {
        this.validateKey(key);
        return this.database.withFunctionHandle(transaction -> transaction
                .prepareStatement(
                        """
                        SELECT `value` FROM `account_metadata`
                        WHERE `account_id` = ? AND `key` = ?
                        """)
                .push(account)
                .push(key)
                .executeSelect((result) -> result.getString("value"))
                .findFirst());
    }

    @Override
    public Map<String, String> selectAllMetadataByPrefix(final int account, final String prefix) {
        this.validateKey(prefix);
        return this.database.withFunctionHandle(transaction -> transaction
                .prepareStatement(
                        """
                        SELECT `key`, `value` FROM `account_metadata`
                        WHERE `account_id` = ? AND `key` LIKE ?
                        """)
                .push(account)
                .push(prefix + "%")
                .executeSelect(result -> Map.entry(result.getString("key"), result.getString("value")))
                .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), HashMap::putAll));
    }

    @Override
    public boolean updateMetadata(final int account, final String key, final String value) {
        this.validateKey(key);
        final var updated = this.database.withFunctionHandle(transaction -> transaction
                .prepareStatement(
                        """
                        INSERT INTO `account_metadata` (`account_id`, `key`, `value`)
                        VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)
                        """)
                .push(account)
                .push(key)
                .push(value)
                .executeSingleUpdate());
        if (updated) {
            this.messenger.broadcast(new MetadataUpdate(account, key, value));
        }
        return updated;
    }

    @Override
    public boolean deleteMetadata(final int account, final String key) {
        this.validateKey(key);
        final var deleted = this.database.withFunctionHandle(transaction -> transaction
                .prepareStatement(
                        """
                        DELETE FROM `account_metadata`
                        WHERE `account_id` = ? AND `key` = ?
                        """)
                .push(account)
                .push(key)
                .executeSingleUpdate());
        if (deleted) {
            this.messenger.broadcast(new MetadataUpdate(account, key, null));
        }
        return deleted;
    }

    private void validateKey(final String key) {
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid key: " + key);
        }
    }
}
