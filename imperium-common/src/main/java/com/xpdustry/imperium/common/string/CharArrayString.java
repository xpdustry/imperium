package com.xpdustry.imperium.common.string;

import java.util.Arrays;

public final class CharArrayString implements CharSequence, AutoCloseable {

    private final char[] chars;

    public CharArrayString(final char[] chars) {
        this.chars = new char[chars.length];
        System.arraycopy(chars, 0, this.chars, 0, chars.length);
    }

    public CharArrayString(final char[] chars, final int start, final int end) {
        this.chars = new char[end - start];
        System.arraycopy(chars, start, this.chars, 0, this.chars.length);
    }

    @Override
    public int length() {
        return this.chars.length;
    }

    @Override
    public char charAt(final int index) {
        return this.chars[index];
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return new CharArrayString(this.chars, start, end);
    }

    @Override
    public void close() {
        Arrays.fill(chars, '\0');
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.chars);
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof CharArrayString that && Arrays.equals(this.chars, that.chars);
    }

    @Override
    public String toString() {
        return new String(this.chars);
    }
}
