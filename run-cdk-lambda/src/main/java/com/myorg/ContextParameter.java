package com.myorg;

import software.amazon.awscdk.App;

import java.util.Optional;

public class ContextParameter {

    protected static final String UNDEFINED_VALUE = "undefined";
    private final App app;

    public static ContextParameter of(final App app) {
        return new ContextParameter(app);
    }

    public Optional<String> get(final String key) {
        final Object value = app.getNode().tryGetContext(key);
        if (value == null || value.toString().equals(UNDEFINED_VALUE)) {
            return Optional.empty();
        } else {
            return Optional.of(value.toString());
        }
    }

    public ContextParameter(final App app) {
        this.app = app;
    }
}
