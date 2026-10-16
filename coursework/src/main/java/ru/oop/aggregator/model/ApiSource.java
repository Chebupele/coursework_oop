package ru.oop.aggregator.model;

import java.net.URI;

public record ApiSource(String name, URI url) {
    public ApiSource {
        if (name == null || !name.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("Invalid API name: " + name);
        }
        if (url == null || url.getHost() == null || !"https".equalsIgnoreCase(url.getScheme())) {
            throw new IllegalArgumentException("API requires an absolute HTTPS URL");
        }
        if (url.getQuery() == null || url.getQuery().isBlank()) {
            throw new IllegalArgumentException("URL must contain query parameters");
        }
    }
}
