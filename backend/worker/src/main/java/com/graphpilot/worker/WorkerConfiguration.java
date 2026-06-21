package com.graphpilot.worker;

import com.graphpilot.adapter.worker.handler.TaskHandlerRegistry;
import com.graphpilot.adapter.worker.http.WorkerTaskController;
import com.graphpilot.scheduler.application.execution.port.in.TaskHandlerProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Worker 模块的装配配置。
 *
 * 职责：
 * - TaskHandlerRegistry（注册 shell/mock handler）
 * - WorkerTaskController（HTTP 端点）
 */
@Configuration
class WorkerConfiguration {

    @Bean
    TaskHandlerProvider taskHandlerProvider() {
        return new TaskHandlerRegistry();
    }

    @Bean
    WorkerTaskController workerTaskController(TaskHandlerProvider taskHandlerProvider) {
        return new WorkerTaskController(taskHandlerProvider);
    }
}