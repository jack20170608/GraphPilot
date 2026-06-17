package com.graphpilot.domain.dag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record TaskConfig(Map<String, Object> values) {

    public TaskConfig {
        Objects.requireNonNull(values, "values must not be null");
        values = Map.copyOf(new LinkedHashMap<>(values));
    }

    public static TaskConfig empty() {
        return new TaskConfig(Map.of());
    }

    public static TaskConfig of(Map<String, Object> values) {
        return new TaskConfig(values);
    }

    public Optional<Object> get(String key) {
        Objects.requireNonNull(key, "key must not be null");
        return Optional.ofNullable(values.get(key));
    }

    public Optional<String> getString(String key) {
        return get(key).map(Object::toString);
    }

    public Optional<Long> getLong(String key) {
        return get(key).map(value -> {
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.parseLong(value.toString());
        });
    }

    public Map<String, Object> asMap() {
        return values;
    }
}
