package com.graphpilot.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphpilot.adapter.persistence.memory.InMemoryWorkflowRepository;
import com.graphpilot.adapter.persistence.memory.InMemoryWorkflowRunRepository;
import com.graphpilot.adapter.persistence.memory.InMemoryWorkflowRunTimelineRepository;
import com.graphpilot.adapter.persistence.memory.SystemClockAdapter;
import com.graphpilot.adapter.persistence.memory.UuidTaskRunIdGenerator;
import com.graphpilot.adapter.persistence.memory.UuidTimelineEventIdGenerator;
import com.graphpilot.adapter.persistence.memory.UuidWorkflowIdGenerator;
import com.graphpilot.adapter.persistence.memory.UuidWorkflowRunIdGenerator;
import com.graphpilot.adapter.worker.handler.MockTaskHandler;
import com.graphpilot.adapter.worker.handler.ShellTaskHandler;
import com.graphpilot.adapter.worker.handler.TaskHandlerRegistry;
import com.graphpilot.adapter.worker.json.JacksonJsonValueCodec;
import com.graphpilot.application.shared.port.ClockPort;
import com.graphpilot.application.shared.port.WorkflowRepository;
import com.graphpilot.scheduler.application.execution.port.in.TaskHandlerProvider;
import com.graphpilot.scheduler.application.execution.port.out.JsonValueCodecPort;
import com.graphpilot.scheduler.application.execution.port.out.TaskRunIdGeneratorPort;
import com.graphpilot.scheduler.application.execution.port.out.TimelineEventIdGeneratorPort;
import com.graphpilot.scheduler.application.execution.port.out.WorkflowRunIdGeneratorPort;
import com.graphpilot.scheduler.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.scheduler.application.execution.port.out.WorkflowRunTimelineRepository;
import com.graphpilot.scheduler.application.execution.port.out.EventPublisherPort;
import com.graphpilot.scheduler.application.execution.service.BackoffStrategy;
import com.graphpilot.scheduler.application.execution.service.FixedBackoffStrategy;
import com.graphpilot.scheduler.application.execution.service.ScanPendingWorkflowRunsService;
import com.graphpilot.scheduler.application.execution.service.TaskConfigExpressionResolver;
import com.graphpilot.scheduler.application.execution.service.TimelineRecorder;
import com.graphpilot.scheduler.application.execution.service.TriggerWorkflowRunService;
import com.graphpilot.scheduler.application.execution.service.WorkflowExecutionCoordinatorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class SchedulerConfiguration {

    // ===== Jackson =====

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    // ===== Domain Ports → Adapters =====

    @Bean
    ClockPort clockPort() {
        return new SystemClockAdapter();
    }

    @Bean
    WorkflowRepository workflowRepository(InMemoryWorkflowRepository delegate) {
        return delegate;
    }

    @Bean
    WorkflowRunRepository workflowRunRepository(InMemoryWorkflowRunRepository delegate) {
        return delegate;
    }

    @Bean
    WorkflowRunTimelineRepository workflowRunTimelineRepository(InMemoryWorkflowRunTimelineRepository delegate) {
        return delegate;
    }

    @Bean
    WorkflowRunIdGeneratorPort workflowRunIdGeneratorPort(UuidWorkflowRunIdGenerator generator) {
        return generator;
    }

    @Bean
    TaskRunIdGeneratorPort taskRunIdGeneratorPort(UuidTaskRunIdGenerator generator) {
        return generator;
    }

    @Bean
    TimelineEventIdGeneratorPort timelineEventIdGeneratorPort(UuidTimelineEventIdGenerator generator) {
        return generator;
    }

    @Bean
    JsonValueCodecPort jsonValueCodecPort(ObjectMapper objectMapper) {
        return new JacksonJsonValueCodec(objectMapper);
    }

    @Bean
    EventPublisherPort eventPublisherPort() {
        return runId -> {}; // No-op for now
    }

    // ===== In-Memory Adapters =====

    @Bean
    InMemoryWorkflowRepository inMemoryWorkflowRepository() {
        return new InMemoryWorkflowRepository();
    }

    @Bean
    InMemoryWorkflowRunRepository inMemoryWorkflowRunRepository() {
        return new InMemoryWorkflowRunRepository();
    }

    @Bean
    InMemoryWorkflowRunTimelineRepository timelineRepository() {
        return new InMemoryWorkflowRunTimelineRepository();
    }

    @Bean
    UuidWorkflowIdGenerator uuidWorkflowIdGenerator() {
        return new UuidWorkflowIdGenerator();
    }

    @Bean
    UuidWorkflowRunIdGenerator uuidWorkflowRunIdGenerator() {
        return new UuidWorkflowRunIdGenerator();
    }

    @Bean
    UuidTaskRunIdGenerator uuidTaskRunIdGenerator() {
        return new UuidTaskRunIdGenerator();
    }

    @Bean
    UuidTimelineEventIdGenerator uuidTimelineEventIdGenerator() {
        return new UuidTimelineEventIdGenerator();
    }

    // ===== Task Handlers =====

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

    // ===== Services =====

    @Bean
    BackoffStrategy backoffStrategy() {
        return new FixedBackoffStrategy(Duration.ofSeconds(5));
    }

    @Bean
    TimelineRecorder timelineRecorder(
            InMemoryWorkflowRunTimelineRepository timelineRepository,
            TimelineEventIdGeneratorPort idGenerator,
            ClockPort clock) {
        return new TimelineRecorder(timelineRepository, idGenerator, clock);
    }

    @Bean
    TaskConfigExpressionResolver taskConfigExpressionResolver(
            WorkflowRunRepository workflowRunRepository,
            JsonValueCodecPort jsonValueCodec) {
        return new TaskConfigExpressionResolver(workflowRunRepository, jsonValueCodec);
    }

    @Bean
    WorkflowExecutionCoordinatorService workflowExecutionCoordinatorService(
            WorkflowRepository workflowRepository,
            WorkflowRunRepository workflowRunRepository,
            TaskHandlerProvider taskHandlerProvider,
            ClockPort clockPort,
            BackoffStrategy backoffStrategy,
            TimelineRecorder timelineRecorder,
            TaskConfigExpressionResolver expressionResolver) {
        return new WorkflowExecutionCoordinatorService(
                workflowRepository,
                workflowRunRepository,
                taskHandlerProvider,
                clockPort,
                backoffStrategy,
                timelineRecorder,
                expressionResolver);
    }

    @Bean
    TriggerWorkflowRunService triggerWorkflowRunService(
            WorkflowRepository workflowRepository,
            WorkflowRunRepository workflowRunRepository,
            WorkflowRunIdGeneratorPort workflowRunIdGenerator,
            TaskRunIdGeneratorPort taskRunIdGenerator,
            ClockPort clockPort,
            EventPublisherPort eventPublisherPort) {
        return new TriggerWorkflowRunService(
                workflowRepository,
                workflowRunRepository,
                workflowRunIdGenerator,
                taskRunIdGenerator,
                clockPort,
                eventPublisherPort);
    }

    @Bean
    ScanPendingWorkflowRunsService scanPendingWorkflowRunsService(
            WorkflowRunRepository workflowRunRepository,
            WorkflowExecutionCoordinatorService executionCoordinator) {
        return new ScanPendingWorkflowRunsService(workflowRunRepository, executionCoordinator);
    }
}