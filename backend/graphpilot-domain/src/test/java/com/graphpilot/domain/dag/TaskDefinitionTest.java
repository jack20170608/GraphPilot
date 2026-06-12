package com.graphpilot.domain.dag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TaskDefinitionTest {

    @Test
    void createsTaskDefinitionWithValidValues() {
        TaskDefinition task = new TaskDefinition(TaskId.of("extract"), "Extract data");

        assertEquals(TaskId.of("extract"), task.id());
        assertEquals("Extract data", task.name());
    }

    @Test
    void trimsTaskId() {
        assertEquals(TaskId.of("extract"), TaskId.of(" extract "));
    }

    @Test
    void rejectsBlankTaskId() {
        assertThrows(IllegalArgumentException.class, () -> TaskId.of(" "));
    }

    @Test
    void trimsTaskName() {
        TaskDefinition task = new TaskDefinition(TaskId.of("extract"), " Extract data ");

        assertEquals("Extract data", task.name());
    }

    @Test
    void rejectsBlankTaskName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TaskDefinition(TaskId.of("extract"), " "));
    }
}
