package com.graphpilot.admin;

import com.graphpilot.adapter.persistence.memory.InMemoryWorkflowRepository;
import com.graphpilot.adapter.persistence.memory.SystemClockAdapter;
import com.graphpilot.adapter.persistence.memory.UuidWorkflowIdGenerator;
import com.graphpilot.adapter.web.spring.execution.WorkflowRunController;
import com.graphpilot.adapter.web.spring.workflow.WorkflowController;
import com.graphpilot.admin.application.workflow.port.in.ChangeWorkflowLifecycleUseCase;
import com.graphpilot.admin.application.workflow.port.in.CreateWorkflowUseCase;
import com.graphpilot.admin.application.workflow.port.in.QueryWorkflowUseCase;
import com.graphpilot.admin.application.workflow.service.ChangeWorkflowLifecycleService;
import com.graphpilot.admin.application.workflow.service.CreateWorkflowService;
import com.graphpilot.admin.application.workflow.service.QueryWorkflowService;
import com.graphpilot.application.shared.port.ClockPort;
import com.graphpilot.application.shared.port.IdGeneratorPort;
import com.graphpilot.application.shared.port.WorkflowRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminConfiguration {

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
    IdGeneratorPort idGeneratorPort(UuidWorkflowIdGenerator generator) {
        return generator;
    }

    // ===== In-Memory Adapters =====

    @Bean
    InMemoryWorkflowRepository inMemoryWorkflowRepository() {
        return new InMemoryWorkflowRepository();
    }

    @Bean
    UuidWorkflowIdGenerator uuidWorkflowIdGenerator() {
        return new UuidWorkflowIdGenerator();
    }

    // ===== Application Services =====

    @Bean
    CreateWorkflowUseCase createWorkflowUseCase(
            WorkflowRepository workflowRepository,
            IdGeneratorPort idGeneratorPort,
            ClockPort clockPort) {
        return new CreateWorkflowService(workflowRepository, idGeneratorPort, clockPort);
    }

    @Bean
    QueryWorkflowUseCase queryWorkflowUseCase(WorkflowRepository workflowRepository) {
        return new QueryWorkflowService(workflowRepository);
    }

    @Bean
    ChangeWorkflowLifecycleUseCase changeWorkflowLifecycleUseCase(
            WorkflowRepository workflowRepository) {
        return new ChangeWorkflowLifecycleService(workflowRepository);
    }

    // ===== REST Controllers (from adapter-web-spring) =====

    @Bean
    WorkflowController workflowController(
            CreateWorkflowUseCase createWorkflowUseCase,
            QueryWorkflowUseCase queryWorkflowUseCase,
            ChangeWorkflowLifecycleUseCase changeWorkflowLifecycleUseCase) {
        return new WorkflowController(createWorkflowUseCase, queryWorkflowUseCase, changeWorkflowLifecycleUseCase);
    }

    @Bean
    WorkflowRunController workflowRunController() {
        return new WorkflowRunController(null, null);
    }
}