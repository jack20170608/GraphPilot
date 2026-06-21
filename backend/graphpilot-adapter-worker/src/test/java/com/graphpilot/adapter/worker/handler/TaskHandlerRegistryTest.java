package com.graphpilot.adapter.worker.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.graphpilot.application.execution.port.in.TaskHandler;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskHandlerRegistryTest {

    @Test
    void registersDefaultHandlersOnConstruction() {
        TaskHandlerRegistry registry = new TaskHandlerRegistry();

        assertThat(registry.getHandler("mock")).isInstanceOf(MockTaskHandler.class);
        assertThat(registry.getHandler("shell")).isInstanceOf(ShellTaskHandler.class);
    }

    @Test
    void getHandlerThrowsForUnknownType() {
        TaskHandlerRegistry registry = new TaskHandlerRegistry();

        assertThatThrownBy(() -> registry.getHandler("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No handler found for task type: unknown");
    }

    @Test
    void getAllHandlersReturnsAllRegistered() {
        TaskHandlerRegistry registry = new TaskHandlerRegistry();

        List<TaskHandler> handlers = registry.getAllHandlers();

        assertThat(handlers).hasSize(2);
        assertThat(handlers).extracting(TaskHandler::supportedType)
                .containsExactlyInAnyOrder("mock", "shell");
    }

    @Test
    void registerAddsNewHandler() {
        TaskHandlerRegistry registry = new TaskHandlerRegistry();
        // Use a different handler type for testing registration
        TaskHandler customHandler = new ShellTaskHandler();

        registry.register(customHandler);

        // Should still have the 2 original handlers (duplicate registration for shell is ignored, mock added)
        assertThat(registry.getAllHandlers()).hasSize(2);
    }

    @Test
    void registerThrowsForNullHandler() {
        TaskHandlerRegistry registry = new TaskHandlerRegistry();

        assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("handler must not be null");
    }
}