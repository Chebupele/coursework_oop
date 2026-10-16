package ru.oop.aggregator.model;

import java.util.Locale;

public enum OutputFormat {
    JSON, CSV;

    public static OutputFormat parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Format must be json or csv");
        }
    }
}
