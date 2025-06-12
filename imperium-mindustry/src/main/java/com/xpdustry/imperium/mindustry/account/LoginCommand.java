package com.xpdustry.imperium.mindustry.account;

import static com.xpdustry.distributor.api.component.ListComponent.components;
import static com.xpdustry.distributor.api.component.TextComponent.*;
import static com.xpdustry.distributor.api.component.TranslatableComponent.translatable;

import com.xpdustry.distributor.api.command.CommandSender;
import com.xpdustry.distributor.api.component.style.ComponentColor;
import com.xpdustry.distributor.api.gui.Action;
import com.xpdustry.distributor.api.gui.Window;
import com.xpdustry.distributor.api.gui.input.TextInputManager;
import com.xpdustry.distributor.api.gui.menu.MenuManager;
import com.xpdustry.distributor.api.key.Key;
import com.xpdustry.distributor.api.player.MUUID;
import com.xpdustry.distributor.api.plugin.MindustryPlugin;
import com.xpdustry.imperium.common.command.ImperiumCommand;
import com.xpdustry.imperium.common.lifecycle.LifecycleListener;
import com.xpdustry.imperium.common.session.MindustrySession;
import com.xpdustry.imperium.common.session.MindustrySessionService;
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide;
import com.xpdustry.imperium.mindustry.gui.FormField;
import com.xpdustry.imperium.mindustry.gui.FormTransformer;
import com.xpdustry.imperium.mindustry.gui.ImperiumActions;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.Executor;
import mindustry.gen.Player;

public final class LoginCommand implements LifecycleListener {

    private static final Key<String> USERNAME_KEY = Key.of("imperium", "username", String.class);
    private static final Key<char[]> PASSWORD_KEY = Key.of("imperium", "password", char[].class);

    private final CachedAccountService cache;
    private final MindustrySessionService sessions;
    private final Executor executor;
    private final MenuManager form;
    private final TextInputManager text;

    @Inject
    public LoginCommand(
            final CachedAccountService cache,
            final MindustryPlugin plugin,
            final MindustrySessionService sessions,
            final @Named("work") Executor executor) {
        this.cache = cache;
        this.sessions = sessions;
        this.executor = executor;
        this.form = MenuManager.create(plugin);
        this.text = TextInputManager.create(plugin);
    }

    @Override
    public void onImperiumInit() {
        this.form.addTransformer(new FormTransformer()
                .textInput(this.text, true)
                .identifier("login")
                .field(FormField.ofText(USERNAME_KEY, FormField.Text.Converter.string()))
                .field(FormField.ofText(PASSWORD_KEY, FormField.Text.Converter.password(List.of())))
                .submitAction(ImperiumActions.delegate(this.executor, this::onLoginFormSubmit)));
    }

    @ImperiumCommand(path = "login")
    @ClientSide
    void onLoginCommand(final CommandSender sender) {
        if (cache.selectCachedAccount(sender.getPlayer()).isPresent()) {
            sender.error("You are already logged in!");
        } else {
            final var window = this.form.create(sender.getPlayer());
            window.show();
            final var disclaimer = components();
            disclaimer.append(translatable("imperium.gui.login.footer"));
            if (true /* TODO REMEMBER_LOGIN */) {
                disclaimer.append(
                        newline(),
                        newline(),
                        translatable("imperium.gui.login.warning.no_remember_login", ComponentColor.SCARLET));
            }
            sender.getAudience().sendAnnouncement(disclaimer.build());
        }
    }

    private MindustrySession.Key key(final Player player) {
        final var address = InetAddress.ofLiteral(player.ip());
        final var muuid = MUUID.from(player);
        return new MindustrySession.Key(muuid.getUuidAsLong(), muuid.getUsidAsLong(), address);
    }

    private Action onLoginFormSubmit(final Window window) {
        final var result = this.sessions.login(
                this.key(window.getViewer()),
                window.getState().getRequired(USERNAME_KEY),
                window.getState().getRequired(PASSWORD_KEY));
        if (result) {
            return ImperiumActions.announce(translatable("imperium.gui.login.success", ComponentColor.GREEN));
        } else {
            return Action.show()
                    .then(ImperiumActions.announce(
                            translatable("imperium.gui.login.failure.invalid-credentials", ComponentColor.SCARLET)));
        }
    }
}
