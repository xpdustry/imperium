package com.xpdustry.imperium.common.string;

import java.util.List;

public final class MissingStringRequirementException extends RuntimeException {

    private final List<StringRequirement> missing;

    public MissingStringRequirementException(final String message, final List<StringRequirement> missing) {
        super(message);
        this.missing = List.copyOf(missing);
    }

    public List<StringRequirement> missing() {
        return missing;
    }
}
