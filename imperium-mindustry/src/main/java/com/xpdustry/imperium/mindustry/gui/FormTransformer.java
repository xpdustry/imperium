package com.xpdustry.imperium.mindustry.gui;

import static com.xpdustry.distributor.api.component.ListComponent.components;
import static com.xpdustry.distributor.api.component.NumberComponent.number;
import static com.xpdustry.distributor.api.component.TextComponent.*;
import static com.xpdustry.distributor.api.component.TranslatableComponent.translatable;

import com.xpdustry.distributor.api.component.Component;
import com.xpdustry.distributor.api.component.style.ComponentColor;
import com.xpdustry.distributor.api.gui.Action;
import com.xpdustry.distributor.api.gui.BiAction;
import com.xpdustry.distributor.api.gui.Window;
import com.xpdustry.distributor.api.gui.input.TextInputManager;
import com.xpdustry.distributor.api.gui.input.TextInputPane;
import com.xpdustry.distributor.api.gui.menu.MenuOption;
import com.xpdustry.distributor.api.gui.menu.MenuPane;
import com.xpdustry.distributor.api.gui.transform.Transformer;
import com.xpdustry.distributor.api.key.Key;
import com.xpdustry.imperium.common.functional.JImperiumResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import mindustry.gen.Iconc;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class FormTransformer implements Transformer<MenuPane> {

    private static final Key<Integer> INDEX_KEY = Key.of("imperium", "index", Integer.class);
    private static final Component NO_TEXT = text("< Empty >", ComponentColor.GRAY);

    private @Nullable TextInputManager textInput = null;
    private String identifier = "unknown";
    private final List<FormField<?>> fields = new ArrayList<>();
    private Action submitAction = Action.none();

    public FormTransformer textInput(final TextInputManager textInput, final boolean install) {
        this.textInput = textInput;
        if (install) {
            this.textInput.addTransformer(this::transformText);
        }
        return this;
    }

    public String identifier() {
        return identifier;
    }

    public FormTransformer identifier(final String identifier) {
        this.identifier = identifier;
        return this;
    }

    public List<FormField<?>> fields() {
        return List.copyOf(fields);
    }

    public FormTransformer field(final FormField<?> field) {
        fields.add(field);
        return this;
    }

    public FormTransformer fields(final List<FormField<?>> fields) {
        this.fields.clear();
        this.fields.addAll(fields);
        return this;
    }

    public Action submitAction() {
        return submitAction;
    }

    public FormTransformer submitAction(final Action submitAction) {
        this.submitAction = submitAction;
        return this;
    }

    @Override
    public void transform(final Context<MenuPane> context) {
        final int index = context.getState().getOptional(INDEX_KEY).orElse(0);
        final var grid = context.getPane().getGrid();

        if (fields.isEmpty() || index < 0 || index >= fields.size()) {
            grid.addRow(MenuOption.of(translatable("imperium.gui.shared.back"), Action.back()));
            return;
        }

        final var field = this.fields.get(index);

        context.getPane()
                .setTitle(components(
                        translatable("imperium.gui." + this.identifier + ".title"),
                        space(),
                        text('('),
                        number(index + 1),
                        text('/'),
                        number(this.fields.size()),
                        text(')')))
                .setContent(translatable("imperium.gui." + this.identifier + ".page."
                        + field.key().getName() + ".description"));

        switch (field) {
            case FormField.Menu<?> m -> this.addMenuElements(context, m);
            case FormField.Text<?> t -> this.addTextElements(context, index, t);
        }

        grid.addRow(MenuOption.of(Iconc.cancel, Action.back()));
        if (index > 0) {
            grid.addOption(MenuOption.of(
                    text(Iconc.left), Action.with(INDEX_KEY, index - 1).then(Action.show())));
        } else {
            grid.addOption(MenuOption.of(text(Iconc.left, ComponentColor.DARK_GRAY), Action.none()));
        }

        final var filled = context.getState().contains(field.key()) || field.optional();

        if (index < this.fields.size() - 1 && filled) {
            grid.addOption(MenuOption.of(
                    text(Iconc.right), Action.with(INDEX_KEY, index + 1).then(Action.show())));
        } else {
            grid.addOption(MenuOption.of(text(Iconc.right, ComponentColor.DARK_GRAY), Action.none()));
        }

        if (index == this.fields.size() - 1 && filled) {
            grid.addOption(MenuOption.of(text(Iconc.upload), Action.hide().then(this.submitAction)));
        } else {
            grid.addOption(MenuOption.of(text(Iconc.upload, ComponentColor.DARK_GRAY), Action.none()));
        }
    }

    private <T> void addMenuElements(final Transformer.Context<MenuPane> context, final FormField.Menu<T> field) {
        final var elements =
                field.elements().apply(context.getViewer(), context.getState()).iterator();
        while (elements.hasNext()) {
            context.getPane().getGrid().addRow();
            for (int x = 0; x < field.width(); x++) {
                final MenuOption option;
                if (elements.hasNext()) {
                    final var element = elements.next();
                    var label = field.renderer().apply(element);
                    if (Objects.equals(context.getState().get(field.key()), element)) {
                        label = components(text('>'), space(), label, space(), text('<'));
                    }
                    option = MenuOption.of(
                            label, Action.with(field.key(), element).then(Window::show));
                } else {
                    option = MenuOption.of();
                }
                context.getPane().getGrid().addOption(option);
            }
        }
    }

    private <T> void addTextElements(
            final Transformer.Context<MenuPane> context, final int index, final FormField.Text<T> field) {
        context.getPane()
                .setContent(components(
                        context.getPane().getContent(),
                        newline(),
                        newline(),
                        context.getState()
                                .getOptional(field.key())
                                .map(field.converter()::toString)
                                .orElse(NO_TEXT),
                        newline()))
                .getGrid()
                .addRow(MenuOption.of(
                        translatable("imperium.gui.shared.edit"),
                        Action.with(INDEX_KEY, index).then(Action.show(Objects.requireNonNull(this.textInput)))));
    }

    private void transformText(final Transformer.Context<TextInputPane> context) {
        context.getPane()
                .setPlaceholder(
                        ((FormField.Text<?>) this.fields.get(context.getState().getRequired(INDEX_KEY))).def())
                .setMaxLength(64)
                .setInputAction(BiAction.delegate(this::onTextSubmit));
    }

    private <T> Action onTextSubmit(final Window window, final String input) {
        @SuppressWarnings("unchecked")
        final var field = (FormField.Text<T>) this.fields.get(window.getState().getRequired(INDEX_KEY));
        return switch (field.converter().fromString(input)) {
            case JImperiumResult.Failure<T, Component> failure -> Action.compose(
                    Action.show(), ImperiumActions.announce(failure.error()));
            case JImperiumResult.Success<T, Component> success -> Action.compose(
                    Action.with(field.key(), success.value()), Action.back());
        };
    }
}
