package com.graphpilot.bootstrap.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphpilot.adapter.worker.json.JacksonJsonValueCodec;
import com.graphpilot.adapter.worker.spring.WorkflowRunEventListener;
import com.graphpilot.adapter.worker.handler.TaskHandlerRegistry;
import com.graphpilot.adapter.worker.http.HttpRemoteTaskHandlerProvider;
import com.graphpilot.application.execution.port.in.ExecuteWorkflowRunUseCase;
import com.graphpilot.application.execution.port.in.ScanPendingWorkflowRunsUseCase;
import com.graphpilot.application.execution.port.in.TaskHandlerProvider;
import com.graphpilot.application.execution.port.out.JsonValueCodecPort;
import com.graphpilot.application.execution.port.out.WorkflowRunRepository;
import com.graphpilot.application.execution.service.FixedBackoffStrategy;
import com.graphpilot.application.execution.service.ScanPendingWorkflowRunsService;
import com.graphpilot.application.execution.service.TaskConfigExpressionResolver;
import com.graphpilot.application.execution.service.TimelineRecorder;
import com.graphpilot.application.execution.service.WorkflowExecutionCoordinatorService;
import com.graphpilot.application.workflow.port.out.ClockPort;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import java.time.Duration;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableScheduling
class WorkerAssemblyConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WorkerAssemblyConfiguration.class);

    /**
     * Fixed delay applied before each task retry. Configurable via
     * {@code graphpilot.worker.retry-backoff-seconds}; keep small for tests.
     */
    @Value("${graphpilot.worker.retry-backoff-seconds:0}")
    private int retryBackoffSeconds;

    /**
     * Task handler provider mode: "local" (default, handler in same process) or "remote" (HTTP dispatch).
     */
    @Value("${graphpilot.worker.dispatch.mode:local}")
    private String dispatchMode;

    /**
     * Remote worker base URL. Only used when dispatch.mode=remote.
     */
    @Value("${graphpilot.worker.dispatch.remote.base-url:http://localhost:8081}")
    private String remoteBaseUrl;

    /**
     * Remote worker HTTP timeout in milliseconds. Only used when dispatch.mode=remote.
     */
    @Value("${graphpilot.worker.dispatch.remote.timeout-ms:90000}")
    private long remoteTimeoutMs;

    @Bean
    @Primary
    TaskHandlerProvider taskHandlerProvider() {
        if ("remote".equalsIgnoreCase(dispatchMode)) {
            log.info("Using REMOTE dispatch mode: tasks will be dispatched to {}", remoteBaseUrl);
            return new HttpRemoteTaskHandlerProvider(remoteBaseUrl, Duration.ofMillis(remoteTimeoutMs));
        } else {
            log.info("Using LOCAL dispatch mode: tasks will be executed in-process");
            return new TaskHandlerRegistry();
        }
    }

    @Bean
    JsonValueCodecPort jsonValueCodecPort() {
        return new JacksonJsonValueCodec(new ObjectMapper());
    }

    @Bean
    TaskConfigExpressionResolver taskConfigExpressionResolver(
            WorkflowRunRepository workflowRunRepository,
            JsonValueCodecPort jsonValueCodecPort) {
        return new TaskConfigExpressionResolver(workflowRunRepository, jsonValueCodecPort);
    }

    @Bean
    ExecuteWorkflowRunUseCase executeWorkflowRunUseCase(
            WorkflowRepository workflowRepository,
            WorkflowRunRepository workflowRunRepository,
            TaskHandlerProvider taskHandlerProvider,
            ClockPort clock,
            TimelineRecorder timelineRecorder,
            TaskConfigExpressionResolver taskConfigExpressionResolver) {
        return new WorkflowExecutionCoordinatorService(
                workflowRepository,
                workflowRunRepository,
                taskHandlerProvider,
                clock,
                new FixedBackoffStrategy(Duration.ofSeconds(retryBackoffSeconds)),
                timelineRecorder,
                taskConfigExpressionResolver);
    }

    @Bean
    ScanPendingWorkflowRunsUseCase scanPendingWorkflowRunsUseCase(
            WorkflowRunRepository workflowRunRepository,
            ExecuteWorkflowRunUseCase executeWorkflowRunUseCase) {
        return new ScanPendingWorkflowRunsService(workflowRunRepository, executeWorkflowRunUseCase);
    }

    @Bean
    WorkflowRunEventListener workflowRunEventListener(
            ExecuteWorkflowRunUseCase executeWorkflowRunUseCase,
            @Qualifier("taskExecutor") Executor taskExecutor) {
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