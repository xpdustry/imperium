package com.xpdustry.imperium.mindustry.account;

import arc.Core;
import com.xpdustry.distributor.api.annotation.EventHandler;
import com.xpdustry.distributor.api.player.MUUID;
import com.xpdustry.imperium.common.account.*;
import com.xpdustry.imperium.common.lifecycle.LifecycleListener;
import com.xpdustry.imperium.common.message.MessageService;
import com.xpdustry.imperium.common.session.MindustrySession;
import com.xpdustry.imperium.common.session.MindustrySessionService;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.net.InetAddress;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;
import mindustry.game.EventType;
import mindustry.gen.Player;

final class CachedAccountServiceImpl implements CachedAccountService, LifecycleListener {

    private final MessageService messages;
    private final AccountService accounts;
    private final AchievementService achievements;
    private final MetadataService metadata;
    private final MindustrySessionService sessions;
    private final Executor executor;
    private final Map<MUUID, CachedAccount> cache = new ConcurrentHashMap<>();

    @Inject
    public CachedAccountServiceImpl(
            final MessageService messages,
            final AccountService accounts,
            final AchievementService achievements,
            final MetadataService metadata,
            final MindustrySessionService sessions,
            final @Named("work") Executor executor) {
        this.messages = messages;
        this.accounts = accounts;
        this.achievements = achievements;
        this.metadata = metadata;
        this.sessions = sessions;
        this.executor = executor;
    }

    @Override
    public void onImperiumInit() {
        this.messages.subscribe(AccountUpdate.class, message -> {
            final var account = this.accounts.selectById(message.account()).orElseThrow();
            this.refresh(account.id(), entry -> entry.account(account));
        });

        this.messages.subscribe(AchievementUpdate.class, message -> {
            this.refresh(message.account(), entry -> entry.achievement(message.achievement(), message.completed()));
        });

        this.messages.subscribe(MetadataUpdate.class, message -> {
            this.refresh(message.account(), entry -> entry.metadata(message.key(), message.value()));
        });
    }

    @Override
    public Optional<CachedAccount> selectCachedAccount(final Player player) {
        return Optional.ofNullable(this.cache.get(MUUID.from(player)));
    }

    @EventHandler
    void onPlayerJoin(final EventType.PlayerJoin event) {
        this.executor.execute(() -> {
            final var muid = MUUID.from(event.player);
            final var session = this.sessions.selectByKey(new MindustrySession.Key(
                    muid.getUuidAsLong(), muid.getUsidAsLong(), InetAddress.ofLiteral(event.player.ip())));
            if (session.isEmpty()) {
                return;
            }
            final var account =
                    this.accounts.selectById(session.get().account()).orElseThrow();
            final var achievements = this.achievements.selectAllAchievements(account.id());
            final var metadata = this.metadata.selectAllMetadata(account.id());
            Core.app.post(() -> {
                if (event.player.con().kicked || event.player.con().hasDisconnected) {
                    return;
                }
                this.cache.put(muid, new CachedAccount(account, achievements, metadata));
            });
        });
    }

    @EventHandler
    void onPlayerQuit(final EventType.PlayerLeave event) {
        this.cache.remove(MUUID.from(event.player));
    }

    private void refresh(final int account, final UnaryOperator<CachedAccount> update) {
        for (final var session : this.sessions.selectAllByAccount(account)) {
            this.cache.computeIfPresent(
                    MUUID.of(session.key().uuid(), session.key().usid()), (ignored, value) -> update.apply(value));
        }
    }
}
