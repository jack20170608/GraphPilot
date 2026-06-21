package com.graphpilot.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Admin 进程 - 工作流管理与前端 API
 *
 * 职责：
 * - Workflow CRUD（DAG 编排）
 * - WorkflowRun 创建与查询
 * - 任务状态查询
 * - 时间线查看
 *
 * 此进程不参与任务执行，只提供管理 API。
 */
@SpringBootApplication
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}