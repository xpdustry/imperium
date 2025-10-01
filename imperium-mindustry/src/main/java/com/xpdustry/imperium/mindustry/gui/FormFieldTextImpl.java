package com.xpdustry.imperium.mindustry.gui;

import static com.xpdustry.distributor.api.component.ListComponent.components;
import static com.xpdustry.distributor.api.component.TextComponent.newline;
import static com.xpdustry.distributor.api.component.TextComponent.text;

import com.xpdustry.distributor.api.component.Component;
import com.xpdustry.distributor.api.component.style.ComponentColor;
import com.xpdustry.distributor.api.component.style.TextStyle;
import com.xpdustry.distributor.api.key.Key;
import com.xpdustry.imperium.common.functional.JImperiumResult;
import com.xpdustry.imperium.common.string.StringRequirement;
import java.util.List;

record FormFieldTextImpl<T>(Key<T> key, boolean optional, Converter<T> converter, String def)
        implements FormField.Text<T> {

    @Override
    public FormFieldTextImpl<T> optional(boolean optional) {
        return new FormFieldTextImpl<>(key, optional, converter, def);
    }

    @Override
    public FormFieldTextImpl<T> converter(Converter<T> converter) {
        return new FormFieldTextImpl<>(key, optional, converter, def);
    }

    @Override
    public FormFieldTextImpl<T> def(String def) {
        return new FormFieldTextImpl<>(key, optional, converter, def);
    }

    enum StringConverter implements Converter<String> {
        INSTANCE;

        @Override
        public JImperiumResult<String, Component> fromString(final String string) {
            return JImperiumResult.success(string);
        }

        @Override
        public Component toString(final String value) {
            return text(value);
        }
    }

    record PasswordConverter(List<StringRequirement> requirements) implements Converter<char[]> {

        @Override
        public JImperiumResult<char[], Component> fromString(final String string) {
            final var missing = requirements.stream()
                    .filter(requirement -> !requirement.isSatisfiedBy(string))
                    .toList();
            if (missing.isEmpty()) {
                return JImperiumResult.success(string.toCharArray());
            }
            final var error = components()
                    .setTextStyle(TextStyle.of(ComponentColor.SCARLET))
                    .append(text("Your input does not meet the following requirements:"));
            for (final var requirement : missing) {
                error.append(newline());
                final var text =
                        switch (requirement) {
                            case StringRequirement.Reserved ignored ->
                                "It's is reserved or already taken, try something else.";
                            case StringRequirement.Length l ->
                                "It needs to be between %d and %d characters long.".formatted(l.min(), l.max());
                            case StringRequirement.AllowedSpecialSymbol s -> {
                                final var builder = new StringBuilder();
                                builder.append("It can only contain the following special symbols: [");
                                final var symbols = s.allowed();
                                for (int i = 0; i < symbols.length; i++) {
                                    builder.append(symbols[i]);
                                    if (i < symbols.length - 1) {
                                        builder.append(", ");
                                    }
                                }
                                yield builder.append("].").toString();
                            }
                            case StringRequirement.Letter l ->
                                switch (l) {
                                    case StringRequirement.Letter.HAS_LOWERCASE ->
                                        "It needs at least one lowercase letter.";
                                    case StringRequirement.Letter.HAS_UPPERCASE ->
                                        "It needs at least one uppercase letter.";
                                    case StringRequirement.Letter.ALL_LOWERCASE -> "Uppercase letters aren't allowed.";
                                    case StringRequirement.Letter.HAS_DIGIT -> "It needs at least a number.";
                                    case StringRequirement.Letter.HAS_SPACIAL_SYMBOL -> "It needs at least a symbol.";
                                };
                        };
                error.append(text(text));
            }
            return JImperiumResult.failure(error.build());
        }

        @Override
        public Component toString(final char[] value) {
            return text("******", ComponentColor.LIGHT_GRAY);
        }
    }
}
