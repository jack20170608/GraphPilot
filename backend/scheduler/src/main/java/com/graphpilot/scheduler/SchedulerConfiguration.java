package com.graphpilot.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphpilot.adapter.worker.json.JacksonJsonValueCodec;
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
import com.graphpilot.domain.execution.WorkflowRunCreatedEvent;
import java.time.Duration;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Scheduler 模块的装配配置。
 *
 * 职责：
 * - TaskHandlerProvider（local/remote 模式）
 * - ExecuteWorkflowRunUseCase（DAG 协调）
 * - ScanPendingWorkflowRunsUseCase（扫描补偿）
 * - 表达式解析器
 * - 事件监听器（内联实现）
 */
@Configuration
@EnableAsync
@EnableScheduling
class SchedulerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SchedulerConfiguration.class);

    @Value("${graphpilot.worker.retry-backoff-seconds:0}")
    private int retryBackoffSeconds;

    @Value("${graphpilot.worker.dispatch.mode:local}")
    private String dispatchMode;

    @Value("${graphpilot.worker.dispatch.remote.base-url:http://localhost:8081}")
    private String remoteBaseUrl;

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

    /**
     * 事件监听器：监听 WorkflowRunCreatedEvent 并触发执行。
     * 已在 scheduler 进程内直接调用，无需独立 listener。
     * 保留此 Bean 只为保持接口兼容，后续可移除。
     */
    @Bean
    Object workflowRunEventHandler(
            ExecuteWorkflowRunUseCase executeWorkflowRunUseCase) {
        return new Object();
    }

    @Bean
    Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("scheduler-");
        executor.initialize();
        return executor;
    }
}