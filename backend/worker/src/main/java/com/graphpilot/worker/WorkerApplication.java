package com.graphpilot.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Worker 进程 - 任务执行器
 *
 * 职责：
 * - 暴露 HTTP 端点接收 scheduler 分发的任务
 * - 使用注册的 handler（shell, mock）执行任务
 * - 返回执行结果
 *
 * 配置：
 * - server.port: 8081 (默认)
 * - graphpilot.worker.handlers: shell,mock
 */
@SpringBootApplication
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}