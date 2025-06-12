package com.xpdustry.imperium.mindustry.account;

import java.util.Optional;
import mindustry.gen.Player;

public interface CachedAccountService {

    Optional<CachedAccount> selectCachedAccount(final Player player);
}
