package com.xpdustry.imperium.mindustry.gui;

import com.xpdustry.distributor.api.component.Component;
import com.xpdustry.distributor.api.key.Key;
import java.util.function.Function;

record FormFieldMenuImpl<T>(
        Key<T> key, boolean optional, ElementProvider<T> elements, Function<T, Component> renderer, int width)
        implements FormField.Menu<T> {

    FormFieldMenuImpl {
        if (width <= 0) throw new IllegalArgumentException("width must be greater than 0, got " + width);
    }

    @Override
    public FormFieldMenuImpl<T> optional(boolean optional) {
        return new FormFieldMenuImpl<>(key, optional, elements, renderer, width);
    }

    @Override
    public FormFieldMenuImpl<T> elements(ElementProvider<T> elements) {
        return new FormFieldMenuImpl<>(key, optional, elements, renderer, width);
    }

    @Override
    public FormFieldMenuImpl<T> renderer(Function<T, Component> renderer) {
        return new FormFieldMenuImpl<>(key, optional, elements, renderer, width);
    }

    @Override
    public FormFieldMenuImpl<T> width(int width) {
        return new FormFieldMenuImpl<>(key, optional, elements, renderer, width);
    }
}
