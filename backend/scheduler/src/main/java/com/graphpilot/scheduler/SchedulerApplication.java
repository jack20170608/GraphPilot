package com.graphpilot.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduler 进程 - DAG 调度核心
 *
 * 职责：
 * - 监听 workflow run 创建事件，触发执行
 * - 扫描 PENDING 状态的任务，进行调度
 * - DAG 依赖解析，顺序执行任务
 * - 重试失败任务
 *
 * 可选配置：
 * - graphpilot.worker.dispatch.mode=local|remote (任务分发模式)
 */
@SpringBootApplication
@EnableScheduling
public class SchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerApplication.class, args);
    }
}