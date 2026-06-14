package com.graphpilot.bootstrap.spring;

import com.graphpilot.adapter.worker.spring.WorkflowRunEventListener;
import com.graphpilot.adapter.worker.spring.handler.TaskHandlerRegistry;
import com.graphpilot.application.execution.port.in.ExecuteWorkflowRunUseCase;
import com.graphpilot.application.execution.port.in.TaskHandlerProvider;
import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.adapter.persistence.mybatis.MyBatisWorkflowRunRepository;
import com.graphpilot.application.execution.service.WorkflowExecutionCoordinatorService;
import com.graphpilot.application.workflow.port.out.ClockPort;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
class WorkerAssemblyConfiguration {

    @Bean
    TaskHandlerProvider taskHandlerProvider() {
        return new TaskHandlerRegistry();
    }

    @Bean
    ExecuteWorkflowRunUseCase executeWorkflowRunUseCase(
            WorkflowRepository workflowRepository,
            WorkflowRunRepository workflowRunRepository,
            TaskHandlerProvider taskHandlerProvider,
            ClockPort clock) {
        return new WorkflowExecutionCoordinatorService(
                workflowRepository,
                workflowRunRepository,
                taskHandlerProvider,
                clock);
    }

    @Bean
    WorkflowRunEventListener workflowRunEventListener(
            ExecuteWorkflowRunUseCase executeWorkflowRunUseCase,
            Executor taskExecutor) {
        return new WorkflowRunEventListener(executeWorkflowRunUseCase, taskExecutor);
    }

    @Bean
    Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("worker-");
        executor.initialize();
        return executor;
    }
}