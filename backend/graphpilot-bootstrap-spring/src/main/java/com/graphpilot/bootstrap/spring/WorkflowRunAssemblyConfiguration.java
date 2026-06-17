package com.graphpilot.bootstrap.spring;

import com.graphpilot.adapter.persistence.memory.InMemoryWorkflowRunRepository;
import com.graphpilot.adapter.persistence.memory.InMemoryWorkflowRunTimelineRepository;
import com.graphpilot.adapter.persistence.memory.UuidTaskRunIdGenerator;
import com.graphpilot.adapter.persistence.memory.UuidTimelineEventIdGenerator;
import com.graphpilot.adapter.persistence.memory.UuidWorkflowRunIdGenerator;
import com.graphpilot.adapter.persistence.mybatis.MyBatisWorkflowRunTimelineRepository;
import com.graphpilot.adapter.persistence.mybatis.mapper.WorkflowRunTimelineMapper;
import com.graphpilot.adapter.worker.spring.config.SpringEventPublisher;
import com.graphpilot.application.execution.port.in.QueryWorkflowRunUseCase;
import com.graphpilot.application.execution.port.in.TriggerWorkflowRunUseCase;
import com.graphpilot.application.execution.port.out.TaskRunIdGeneratorPort;
import com.graphpilot.application.execution.port.out.TimelineEventIdGeneratorPort;
import com.graphpilot.application.execution.port.out.WorkflowRunIdGeneratorPort;
import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.application.execution.port.out.WorkflowRunTimelineRepository;
import com.graphpilot.application.execution.service.QueryWorkflowRunService;
import com.graphpilot.application.execution.service.TimelineRecorder;
import com.graphpilot.application.execution.service.TriggerWorkflowRunService;
import com.graphpilot.application.workflow.port.out.ClockPort;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
class WorkflowRunAssemblyConfiguration {

    @Bean
    @Profile("!postgres")
    WorkflowRunRepository workflowRunRepository() {
        return new InMemoryWorkflowRunRepository();
    }

    @Bean
    WorkflowRunIdGeneratorPort workflowRunIdGeneratorPort() {
        return new UuidWorkflowRunIdGenerator();
    }

    @Bean
    @Profile("!postgres")
    WorkflowRunTimelineRepository workflowRunTimelineRepository() {
        return new InMemoryWorkflowRunTimelineRepository();
    }

    @Bean
    @Profile("postgres")
    WorkflowRunTimelineRepository myBatisWorkflowRunTimelineRepository(WorkflowRunTimelineMapper mapper) {
        return new MyBatisWorkflowRunTimelineRepository(mapper);
    }

    @Bean
    TimelineEventIdGeneratorPort timelineEventIdGeneratorPort() {
        return new UuidTimelineEventIdGenerator();
    }

    @Bean
    TaskRunIdGeneratorPort taskRunIdGeneratorPort() {
        return new UuidTaskRunIdGenerator();
    }

    @Bean
    TimelineRecorder timelineRecorder(
            WorkflowRunTimelineRepository timelineRepository,
            TimelineEventIdGeneratorPort timelineEventIdGenerator,
            ClockPort clock) {
        return new TimelineRecorder(timelineRepository, timelineEventIdGenerator, clock);
    }

    @Bean
    TriggerWorkflowRunUseCase triggerWorkflowRunUseCase(
            WorkflowRepository workflowRepository,
            WorkflowRunRepository workflowRunRepository,
            WorkflowRunIdGeneratorPort workflowRunIdGenerator,
            TaskRunIdGeneratorPort taskRunIdGenerator,
            ClockPort clock,
            SpringEventPublisher eventPublisher,
            TimelineRecorder timelineRecorder) {
        TriggerWorkflowRunUseCase delegate = new TriggerWorkflowRunService(
                workflowRepository,
                workflowRunRepository,
                workflowRunIdGenerator,
                taskRunIdGenerator,
                clock,
                eventPublisher,
                timelineRecorder);
        return new TransactionalTriggerWorkflowRunUseCase(delegate);
    }

    @Bean
    QueryWorkflowRunUseCase queryWorkflowRunUseCase(
            WorkflowRunRepository workflowRunRepository,
            WorkflowRunTimelineRepository timelineRepository) {
        return new QueryWorkflowRunService(workflowRunRepository, timelineRepository);
    }
}