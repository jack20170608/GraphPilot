package com.graphpilot.bootstrap.spring;

import com.graphpilot.adapter.persistence.memory.InMemoryWorkflowRunRepository;
import com.graphpilot.adapter.persistence.memory.UuidTaskRunIdGenerator;
import com.graphpilot.adapter.persistence.memory.UuidWorkflowRunIdGenerator;
import com.graphpilot.application.execution.port.in.QueryWorkflowRunUseCase;
import com.graphpilot.application.execution.port.in.TriggerWorkflowRunUseCase;
import com.graphpilot.application.execution.port.out.TaskRunIdGeneratorPort;
import com.graphpilot.application.execution.port.out.WorkflowRunIdGeneratorPort;
import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.application.execution.service.QueryWorkflowRunService;
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
    TaskRunIdGeneratorPort taskRunIdGeneratorPort() {
        return new UuidTaskRunIdGenerator();
    }

    @Bean
    TriggerWorkflowRunUseCase triggerWorkflowRunUseCase(
            WorkflowRepository workflowRepository,
            WorkflowRunRepository workflowRunRepository,
            WorkflowRunIdGeneratorPort workflowRunIdGenerator,
            TaskRunIdGeneratorPort taskRunIdGenerator,
            ClockPort clock) {
        return new TriggerWorkflowRunService(
                workflowRepository,
                workflowRunRepository,
                workflowRunIdGenerator,
                taskRunIdGenerator,
                clock);
    }

    @Bean
    QueryWorkflowRunUseCase queryWorkflowRunUseCase(WorkflowRunRepository workflowRunRepository) {
        return new QueryWorkflowRunService(workflowRunRepository);
    }
}
