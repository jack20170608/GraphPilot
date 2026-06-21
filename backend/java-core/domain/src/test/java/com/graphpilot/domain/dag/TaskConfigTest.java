package com.graphpilot.domain.dag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskConfigTest {

    @Test
    void emptyReturnsImmutableEmptyConfig() {
        TaskConfig config = TaskConfig.empty();

        assertTrue(config.asMap().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> config.asMap().put("command", "echo hi"));
    }

    @Test
    void copiesInputMapDefensively() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("command", "echo hi");

        TaskConfig config = TaskConfig.of(values);
        values.put("command", "rm -rf /tmp/noop");

        assertEquals("echo hi", config.getString("command").orElseThrow());
    }

    @Test
    void rejectsNullMap() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> TaskConfig.of(null));

        assertEquals("values must not be null", exception.getMessage());
    }

    @Test
    void readsStringAndLongValues() {
        TaskConfig config = TaskConfig.of(Map.of(
                "command", "echo hi",
                "timeout", 10));

        assertEquals("echo hi", config.getString("command").orElseThrow());
        assertEquals(10L, config.getLong("timeout").orElseThrow());
        assertFalse(config.getString("missing").isPresent());
        assertFalse(config.getLong("missing").isPresent());
    }
}
