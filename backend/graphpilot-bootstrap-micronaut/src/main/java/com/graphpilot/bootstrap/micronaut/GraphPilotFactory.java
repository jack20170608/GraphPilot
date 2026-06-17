package com.graphpilot.bootstrap.micronaut;

import com.graphpilot.adapter.persistence.memory.InMemoryWorkflowRepository;
import com.graphpilot.adapter.persistence.memory.InMemoryWorkflowRunRepository;
import com.graphpilot.adapter.persistence.memory.SystemClockAdapter;
import com.graphpilot.adapter.persistence.memory.UuidTaskRunIdGenerator;
import com.graphpilot.adapter.persistence.memory.UuidWorkflowIdGenerator;
import com.graphpilot.adapter.persistence.memory.UuidWorkflowRunIdGenerator;
import com.graphpilot.adapter.worker.handler.TaskHandlerRegistry;
import com.graphpilot.adapter.worker.micronaut.MicronautEventPublisher;
import com.graphpilot.adapter.worker.micronaut.WorkflowRunCreatedApplicationEvent;
import com.graphpilot.adapter.worker.micronaut.WorkflowRunEventListener;
import com.graphpilot.application.execution.port.in.ExecuteWorkflowRunUseCase;
import com.graphpilot.application.execution.port.in.QueryWorkflowRunUseCase;
import com.graphpilot.application.execution.port.in.TaskHandlerProvider;
import com.graphpilot.application.execution.port.in.TriggerWorkflowRunUseCase;
import com.graphpilot.application.execution.port.out.EventPublisherPort;
import com.graphpilot.application.execution.port.out.TaskRunIdGeneratorPort;
import com.graphpilot.application.execution.port.out.WorkflowRunIdGeneratorPort;
import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.application.execution.service.FixedBackoffStrategy;
import com.graphpilot.application.execution.service.QueryWorkflowRunService;
import com.graphpilot.application.execution.service.WorkflowExecutionCoordinatorService;
import com.graphpilot.application.execution.service.TriggerWorkflowRunService;
import com.graphpilot.application.workflow.port.in.ChangeWorkflowLifecycleUseCase;
import com.graphpilot.application.workflow.port.in.CreateWorkflowUseCase;
import com.graphpilot.application.workflow.port.in.QueryWorkflowUseCase;
import com.graphpilot.application.workflow.port.out.ClockPort;
import com.graphpilot.application.workflow.port.out.IdGeneratorPort;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import com.graphpilot.application.workflow.service.ChangeWorkflowLifecycleService;
import com.graphpilot.application.workflow.service.CreateWorkflowService;
import com.graphpilot.application.workflow.service.QueryWorkflowService;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Singleton;
import java.time.Duration;

/**
 * Assembles the framework-free application core and in-memory persistence adapters
 * into the Micronaut context, mirroring the Spring bootstrap's in-memory profile.
 *
 * <p>The adapters have no Micronaut annotations (they are framework-free), so a
 * {@link Factory} produces them as singletons — the same objects the Spring
 * bootstrap builds via {@code @Bean} methods.
 */
@Factory
final class GraphPilotFactory {

    @Singleton
    WorkflowRepository workflowRepository() {
        return new InMemoryWorkflowRepository();
    }

    @Singleton
    WorkflowRunRepository workflowRunRepository() {
        return new InMemoryWorkflowRunRepository();
    }

    @Singleton
    IdGeneratorPort idGeneratorPort() {
        return new UuidWorkflowIdGenerator();
    }

    @Singleton
    WorkflowRunIdGeneratorPort workflowRunIdGeneratorPort() {
        return new UuidWorkflowRunIdGenerator();
    }

    @Singleton
    TaskRunIdGeneratorPort taskRunIdGeneratorPort() {
        return new UuidTaskRunIdGenerator();
    }

    @Singleton
    ClockPort clockPort() {
        return new SystemClockAdapter();
    }

    @Singleton
    TaskHandlerProvider taskHandlerProvider() {
        TaskHandlerRegistry registry = new TaskHandlerRegistry();
        registry.register(new PocTaskHandler());
        return registry;
    }

    @Singleton
    ExecuteWorkflowRunUseCase executeWorkflowRunUseCase(
            WorkflowRepository workflowRepository,
            WorkflowRunRepository workflowRunRepository,
            TaskHandlerProvider taskHandlerProvider,
            ClockPort clock) {
        // Zero backoff keeps the PoC fast; the framework-free core is unchanged.
        return new WorkflowExecutionCoordinatorService(
                workflowRepository,
                workflowRunRepository,
                taskHandlerProvider,
                clock,
                new FixedBackoffStrategy(Duration.ZERO));
    }

    @Singleton
    EventPublisherPort eventPublisherPort(
            ApplicationEventPublisher<WorkflowRunCreatedApplicationEvent> eventPublisher) {
        return new MicronautEventPublisher(eventPublisher);
    }

    @Singleton
    WorkflowRunEventListener workflowRunEventListener(ExecuteWorkflowRunUseCase executeWorkflowRunUseCase) {
        return new WorkflowRunEventListener(executeWorkflowRunUseCase);
    }

    @Singleton
    TriggerWorkflowRunUseCase triggerWorkflowRunUseCase(
            WorkflowRepository workflowRepository,
            WorkflowRunRepository workflowRunRepository,
            WorkflowRunIdGeneratorPort workflowRunIdGenerator,
            TaskRunIdGeneratorPort taskRunIdGenerator,
            ClockPort clock,
            EventPublisherPort eventPublisher) {
        return new TriggerWorkflowRunService(
                workflowRepository,
                workflowRunRepository,
                workflowRunIdGenerator,
                taskRunIdGenerator,
                clock,
                eventPublisher);
    }

    @Singleton
    QueryWorkflowRunUseCase queryWorkflowRunUseCase(WorkflowRunRepository workflowRunRepository) {
        return new QueryWorkflowRunService(workflowRunRepository);
    }

    @Singleton
    CreateWorkflowUseCase createWorkflowUseCase(
            WorkflowRepository workflowRepository,
            IdGeneratorPort idGenerator,
            ClockPort clock) {
        return new CreateWorkflowService(workflowRepository, idGenerator, clock);
    }

    @Singleton
    ChangeWorkflowLifecycleUseCase changeWorkflowLifecycleUseCase(WorkflowRepository workflowRepository) {
        return new ChangeWorkflowLifecycleService(workflowRepository);
    }

    @Singleton
    QueryWorkflowUseCase queryWorkflowUseCase(WorkflowRepository workflowRepository) {
        return new QueryWorkflowService(workflowRepository);
    }
}
