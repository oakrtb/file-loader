package com.oakrtb.file.loader.view;

import java.util.Objects;

/**
 * Named {@link MemoryView} registration for {@link com.oakrtb.file.loader.FileLoader}.
 * In Spring, expose as {@code @Bean} and the starter will pick them up automatically.
 */
public record ViewRegistration(String name, MemoryView<?> view) {

    public ViewRegistration {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(view, "view");
    }

    public static ViewRegistration of(String name, MemoryView<?> view) {
        return new ViewRegistration(name, view);
    }
}
