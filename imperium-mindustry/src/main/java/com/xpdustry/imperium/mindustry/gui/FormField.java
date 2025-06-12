package com.xpdustry.imperium.mindustry.gui;

import com.xpdustry.distributor.api.component.Component;
import com.xpdustry.distributor.api.component.TextComponent;
import com.xpdustry.distributor.api.key.Key;
import com.xpdustry.distributor.api.key.KeyContainer;
import com.xpdustry.imperium.common.functional.JImperiumResult;
import com.xpdustry.imperium.common.string.StringRequirement;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import mindustry.gen.Player;

public sealed interface FormField<T> {

    static <T> FormField.Menu<T> ofMenu(final Key<T> key, final Menu.ElementProvider<T> elements) {
        return new FormFieldMenuImpl<>(key, false, elements, element -> TextComponent.text(element.toString()), 1);
    }

    static <T> FormField.Text<T> ofText(final Key<T> key, final FormField.Text.Converter<T> converter) {
        return new FormFieldTextImpl<>(key, false, converter, "");
    }

    Key<T> key();

    boolean optional();

    FormField<T> optional(final boolean optional);

    sealed interface Menu<T> extends FormField<T> permits FormFieldMenuImpl {

        ElementProvider<T> elements();

        Menu<T> elements(final ElementProvider<T> elements);

        Function<T, Component> renderer();

        Menu<T> renderer(final Function<T, Component> renderer);

        int width();

        Menu<T> width(final int width);

        @FunctionalInterface
        interface ElementProvider<T> extends BiFunction<Player, KeyContainer, List<T>> {}
    }

    sealed interface Text<T> extends FormField<T> permits FormFieldTextImpl {

        Converter<T> converter();

        Text<T> converter(final Converter<T> converter);

        String def();

        Text<T> def(final String def);

        interface Converter<T> {

            JImperiumResult<T, Component> fromString(final String string);

            Component toString(final T value);

            static Converter<String> string() {
                return FormFieldTextImpl.StringConverter.INSTANCE;
            }

            static Converter<char[]> password(final List<StringRequirement> requirements) {
                return new FormFieldTextImpl.PasswordConverter(requirements);
            }
        }
    }
}
