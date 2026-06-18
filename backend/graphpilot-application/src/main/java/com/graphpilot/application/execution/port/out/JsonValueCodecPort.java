package com.graphpilot.application.execution.port.out;

/**
 * Framework-free port for JSON parsing and stringifying.
 * Implementations are provided by adapter modules so the application layer
 * remains free of serialization libraries.
 */
public interface JsonValueCodecPort {

    /**
     * Parses a JSON string into a Java object (Map, List, String, Number, Boolean, or null).
     *
     * @param json the JSON string to parse
     * @return the parsed object
     * @throws RuntimeException if the string is not valid JSON
     */
    Object parse(String json);

    /**
     * Stringifies a Java object to a JSON string.
     *
     * @param value the value to stringify
     * @return the JSON string representation
     * @throws RuntimeException if the value cannot be serialized
     */
    String stringify(Object value);
}
