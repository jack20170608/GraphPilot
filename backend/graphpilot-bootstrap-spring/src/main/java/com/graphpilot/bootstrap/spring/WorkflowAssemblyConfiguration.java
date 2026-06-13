package com.graphpilot.bootstrap.spring;

import com.graphpilot.adapter.persistence.memory.InMemoryWorkflowRepository;
import com.graphpilot.adapter.persistence.memory.SystemClockAdapter;
import com.graphpilot.adapter.persistence.memory.UuidWorkflowIdGenerator;
import com.graphpilot.application.workflow.port.in.CreateWorkflowUseCase;
import com.graphpilot.application.workflow.port.in.QueryWorkflowUseCase;
import com.graphpilot.application.workflow.port.out.ClockPort;
import com.graphpilot.application.workflow.port.out.IdGeneratorPort;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import com.graphpilot.application.workflow.service.CreateWorkflowService;
import com.graphpilot.application.workflow.service.QueryWorkflowService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class WorkflowAssemblyConfiguration {

    @Bean
    WorkflowRepository workflowRepository() {
        return new InMemoryWorkflowRepository();
    }

    @Bean
    IdGeneratorPort idGeneratorPort() {
        return new UuidWorkflowIdGenerator();
    }

    @Bean
    ClockPort clockPort() {
        return new SystemClockAdapter();
    }

    @Bean
    CreateWorkflowUseCase createWorkflowUseCase(
            WorkflowRepository workflowRepository,
            IdGeneratorPort idGenerator,
            ClockPort clock) {
        return new CreateWorkflowService(workflowRepository, idGenerator, clock);
    }

    @Bean
    QueryWorkflowUseCase queryWorkflowUseCase(WorkflowRepository workflowRepository) {
        return new QueryWorkflowService(workflowRepository);
    }
}
