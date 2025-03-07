package com.xpdustry.imperium.common.string;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntPredicate;

public sealed interface StringRequirement {

    List<StringRequirement> DEFAULT_USERNAME_REQUIREMENTS = getUsernameRequirements();

    List<StringRequirement> DEFAULT_PASSWORD_REQUIREMENTS = getPasswordRequirements();

    boolean isSatisfiedBy(final CharSequence string);

    default boolean isSatisfiedBy(final char[] string) {
        try (final var wrapped = new CharArrayString(string)) {
            return this.isSatisfiedBy(wrapped);
        }
    }

    enum Letter implements StringRequirement {
        HAS_LOWERCASE(Character::isLowerCase, false),
        HAS_UPPERCASE(Character::isUpperCase, false),
        HAS_DIGIT(Character::isDigit, false),
        HAS_SPACIAL_SYMBOL(c -> !Character.isLetterOrDigit(c), false),
        ALL_LOWERCASE(Character::isLowerCase, true);

        private final IntPredicate predicate;
        private final boolean all;

        Letter(final IntPredicate predicate, final boolean all) {
            this.predicate = predicate;
            this.all = all;
        }

        @Override
        public boolean isSatisfiedBy(final CharSequence string) {
            return this.all
                    ? string.codePoints().allMatch(this.predicate)
                    : string.codePoints().anyMatch(this.predicate);
        }
    }

    record AllowedSpecialSymbol(char... allowed) implements StringRequirement {

        public AllowedSpecialSymbol(final char... allowed) {
            this.allowed = allowed.clone();
        }

        @Override
        public char[] allowed() {
            return this.allowed.clone();
        }

        @Override
        public boolean isSatisfiedBy(final CharSequence string) {
            for (int i = 0; i < string.length(); i++) {
                final var c = string.charAt(i);
                if (Character.isLetterOrDigit(c)) {
                    continue;
                }
                for (final char a : this.allowed) {
                    if (c != a) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    record Length(int min, int max) implements StringRequirement {
        @Override
        public boolean isSatisfiedBy(final CharSequence value) {
            return value.length() >= this.min && value.length() <= this.max;
        }
    }

    record Reserved(StringTrieMap<Boolean> reserved) implements StringRequirement {

        @Override
        public boolean isSatisfiedBy(final CharSequence value) {
            return this.reserved.contains(value, false);
        }
    }

    private static List<StringRequirement> getUsernameRequirements() {
        final StringTrieMap.Mutable<Boolean> reserved = StringTrieMap.create();
        try (final var stream = StringRequirement.class.getResourceAsStream(
                        "/com/xpdustry/imperium/common/string/reserved-usernames.txt");
                final var reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(stream)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().toLowerCase(Locale.ROOT);
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                reserved.put(line, Boolean.TRUE);
            }
        } catch (final Exception e) {
            throw new RuntimeException("Failed to load reserved usernames: " + e);
        }

        return List.of(
                StringRequirement.Letter.ALL_LOWERCASE,
                new StringRequirement.AllowedSpecialSymbol('_'),
                new StringRequirement.Length(3, 32),
                new StringRequirement.Reserved(reserved));
    }

    private static List<StringRequirement> getPasswordRequirements() {
        return List.of(
                StringRequirement.Letter.HAS_LOWERCASE,
                StringRequirement.Letter.HAS_UPPERCASE,
                StringRequirement.Letter.HAS_DIGIT,
                StringRequirement.Letter.HAS_SPACIAL_SYMBOL,
                new StringRequirement.Length(8, 64));
    }
}
