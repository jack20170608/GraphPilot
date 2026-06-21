package com.graphpilot.worker;

import com.graphpilot.adapter.worker.handler.MockTaskHandler;
import com.graphpilot.adapter.worker.handler.ShellTaskHandler;
import com.graphpilot.adapter.worker.handler.TaskHandlerRegistry;
import com.graphpilot.scheduler.application.execution.port.in.TaskHandlerProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkerConfiguration {

    @Bean
    ShellTaskHandler shellTaskHandler() {
        return new ShellTaskHandler();
    }

    @Bean
    MockTaskHandler mockTaskHandler() {
        return new MockTaskHandler();
    }

    @Bean
    TaskHandlerRegistry taskHandlerRegistry(ShellTaskHandler shellTaskHandler, MockTaskHandler mockTaskHandler) {
        TaskHandlerRegistry registry = new TaskHandlerRegistry();
        registry.register(shellTaskHandler);
        registry.register(mockTaskHandler);
        return registry;
    }

    @Bean
    TaskHandlerProvider taskHandlerProvider(TaskHandlerRegistry registry) {
        return registry;
    }
}