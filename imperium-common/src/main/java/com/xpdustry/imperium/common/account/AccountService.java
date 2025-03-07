package com.xpdustry.imperium.common.account;

import com.xpdustry.imperium.common.password.PasswordHash;
import com.xpdustry.imperium.common.string.StringRequirement;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface AccountService {

    boolean register(final String username, final char[] password);

    boolean updatePassword(final int id, final char[] password);

    Optional<Account> selectById(final int id);

    boolean existsById(final int id);

    Optional<Account> selectByUsername(final String username);

    boolean existsByUsername(final String username);

    Optional<Account> selectByDiscord(final long discord);

    boolean updateDiscord(final int id, final long discord);

    boolean incrementPlaytime(final int id, final Duration duration);

    boolean updateRank(final int id, final Rank rank);

    Optional<PasswordHash> selectPasswordById(final int id);

    List<StringRequirement> usernameRequirements();

    List<StringRequirement> passwordRequirements();
}
