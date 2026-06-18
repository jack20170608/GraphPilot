package com.graphpilot.adapter.worker.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphpilot.application.execution.port.out.JsonValueCodecPort;
import java.util.Objects;

/**
 * Jackson-backed implementation of {@link JsonValueCodecPort}.
 * Provided in the framework-free worker adapter so the application layer
 * does not depend on Jackson directly.
 */
public final class JacksonJsonValueCodec implements JsonValueCodecPort {

    private final ObjectMapper objectMapper;

    public JacksonJsonValueCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public Object parse(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }

    @Override
    public String stringify(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to stringify value to JSON", e);
        }
    }
}
