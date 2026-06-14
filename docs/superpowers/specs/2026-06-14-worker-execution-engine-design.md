# Worker Execution Engine 设计

日期：2026-06-14

## 背景

GraphPilot 当前已实现 Workflow Run MVP：用户可以手动触发 ACTIVE Workflow 创建 PENDING 状态的 WorkflowRun 和 TaskRuns。

下一步需要实现 Worker 执行引擎，驱动 DAG 中的任务实际执行，并支持：
1. 事件驱动执行：Trigger 后自动开始
2. DAG 拓扑序调度：按依赖关系执行任务
3. 可扩展 Handler：支持 HTTP/Shell/Mock 等任务类型
4. 重试机制：失败自动重试

## 目标

1. Trigger 后 Worker 自动消费 PENDING run 并执行
2. 按 DAG 拓扑序调度 Task，满足依赖关系
3. Task 执行状态实时更新：`PENDING` → `RUNNING` → `SUCCEEDED`/`FAILED`
4. WorkflowRun 状态随 Task 完成而演进
5. 支持可扩展的 TaskHandler 机制
6. 失败重试机制（默认 3 次）

## 非目标

- 分布式 Worker 或多节点协调
- Cron 定时调度
- Task 输出存储或 artifact 管理
- 并行执行优化（单线程 MVP）
- 可视化执行日志流

## Domain 设计

### 1. 更新 TaskDefinition

添加 `type` 字段标识任务类型：

```java
public record TaskDefinition(
    TaskId id, 
    String name,
    String type  // "http", "shell", "mock"
) {
    public TaskDefinition {
        // ... validation
        type = (type == null || type.isBlank()) ? "mock" : type.trim();
    }
}
```

### 2. TaskResult Value Object

记录任务执行结果：

```java
public record TaskResult(
    TaskRunStatus status,
    Optional<String> output,
    Optional<String> error,
    Optional<String> errorMessage
) {
    public static TaskResult success(String output) { ... }
    public static TaskResult failure(String error, String message) { ... }
}
```

### 3. WorkflowRunCreatedEvent

在 domain 层定义事件（用于 port 隔离后的发布）：

```java
public record WorkflowRunCreatedEvent(
    WorkflowRunId workflowRunId,
    WorkflowId workflowId
) {}
```

## Application 设计

### 1. TaskHandler 接口

```java
public interface TaskHandler {
    String supportedType();
    TaskResult execute(TaskRun taskRun, TaskDefinition task, Map<String, Object> input);
}
```

### 2. EventPublisherPort

```java
public interface EventPublisherPort {
    void publish(WorkflowRunCreatedEvent event);
}
```

### 3. TaskRunRepository（新增，扩展现有）

```java
public interface TaskRunRepository {
    // 现有方法
    List<TaskRun> findTaskRunsByRunId(WorkflowRunId workflowRunId);
    
    // 新增：更新 TaskRun 状态
    TaskRun updateStatus(TaskRunId taskRunId, TaskRunStatus status, TaskResult result);
    
    // 新增：查找可执行的 Task（依赖已满足）
    List<TaskRun> findRunnableTasks(WorkflowRunId workflowRunId);
    
    // 新增：标记 Task 为 RUNNING
    TaskRun markRunning(TaskRunId taskRunId, Instant startedAt);
}
```

### 4. ExecuteWorkflowRunUseCase

```java
public interface ExecuteWorkflowRunUseCase {
    void execute(WorkflowRunId workflowRunId);
}
```

### 5. WorkflowExecutionCoordinatorService

核心编排服务：

```java
public class WorkflowExecutionCoordinatorService {
    // 1. 加载 run 和 task runs
    // 2. 查找所有依赖已满足的 runnable tasks
    // 3. 按 position 顺序执行（单线程）
    // 4. 更新 task 状态
    // 5. 检查是否全部完成，更新 run 状态
    
    void execute(WorkflowRunId workflowRunId) {
        List<TaskRun> taskRuns = taskRunRepository.findTaskRunsByRunId(workflowRunId);
        // DAG 依赖分析：找出所有依赖 task 已 SUCCEEDED 的 PENDING task
        List<TaskRun> runnable = findRunnableTasks(taskRuns);
        
        for (TaskRun taskRun : runnable) {
            executeTask(taskRun);
        }
        
        // 检查整体状态
        updateWorkflowRunStatus(workflowRunId, taskRuns);
    }
}
```

### 6. RetryService

```java
public class RetryService {
    boolean shouldRetry(TaskRun taskRun) {
        return taskRun.status() == FAILED 
            && taskRun.retryCount() < taskRun.maxRetries();
    }
    
    void scheduleRetry(TaskRun taskRun) {
        // MVP: 立即重试，通过递归调用 Coordinator
        coordinator.execute(taskRun.workflowRunId());
    }
}
```

## Worker Adapter 设计

### 模块结构

`graphpilot-adapter-worker-spring` - Spring 适配器

### 1. WorkflowRunEventListener

监听事件并触发执行：

```java
@Component
public class WorkflowRunEventListener {
    
    @EventListener
    void onWorkflowRunCreated(WorkflowRunCreatedEvent event) {
        // 异步执行，避免阻塞发布者
        executor.execute(() -> useCase.execute(event.workflowRunId()));
    }
}
```

### 2. TaskHandlerRegistry

管理所有注册的 Handler：

```java
@Component
public class TaskHandlerRegistry {
    private final Map<String, TaskHandler> handlers = new ConcurrentHashMap<>();
    
    public void register(TaskHandler handler) {
        handlers.put(handler.supportedType(), handler);
    }
    
    public TaskHandler get(String type) {
        TaskHandler handler = handlers.get(type);
        if (handler == null) {
            throw new TaskHandlerNotFoundException(type);
        }
        return handler;
    }
}
```

### 3. Built-in Handlers

- **HttpTaskHandler**: 发送 HTTP 请求
- **ShellTaskHandler**: 执行本地 Shell 命令
- **MockTaskHandler**: 模拟执行（固定延迟后成功）

### 4. HttpTaskHandler Detail

```java
@Component
public class HttpTaskHandler implements TaskHandler {
    
    @Override
    public String supportedType() {
        return "http";
    }
    
    @Override
    public TaskResult execute(TaskRun taskRun, TaskDefinition task, Map<String, Object> input) {
        HttpRequest request = buildRequest(input);
        try {
            HttpResponse response = httpClient.send(request);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return TaskResult.success(response.body());
            }
            return TaskResult.failure("HTTP " + response.statusCode(), response.body());
        } catch (Exception e) {
            return TaskResult.failure(e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
```

## Persistence 设计

### V4 Migration

```sql
-- TaskRuns 重试和执行相关字段
ALTER TABLE task_runs ADD COLUMN retry_count integer NOT NULL DEFAULT 0;
ALTER TABLE task_runs ADD COLUMN max_retries integer NOT NULL DEFAULT 3;
ALTER TABLE task_runs ADD COLUMN error_message text;
ALTER TABLE task_runs ADD COLUMN attempted_at timestamptz;
ALTER TABLE task_runs ADD COLUMN started_at timestamptz;
ALTER TABLE task_runs ADD COLUMN finished_at timestamptz;

-- Task 输入输出（JSON）
ALTER TABLE task_runs ADD COLUMN input_data jsonb;
ALTER TABLE task_runs ADD COLUMN output_data jsonb;

-- WorkflowRuns 执行时间
ALTER TABLE workflow_runs ADD COLUMN started_at timestamptz;
ALTER TABLE workflow_runs ADD COLUMN finished_at timestamptz;

-- 便于查找可执行 task 的索引
CREATE INDEX idx_task_runs_status ON task_runs(workflow_run_id, status);
```

### Row Updates

`TaskRunRow` 和 `WorkflowRunRow` 添加对应字段。

### Mapper Updates

- `updateTaskRunStatus`: 更新状态、错误信息、时间
- `findPendingTaskRunsByRunId`: 查找 PENDING 的 task runs
- `findRunnableTasks`: 查找依赖已满足的 PENDING tasks

## Bootstrap 设计

### 新模块

`graphpilot-adapter-worker-spring` 依赖：
- `graphpilot-application` (execution ports)
- Spring Boot Starter

### 配置

```java
@Configuration
public class WorkerAssemblyConfiguration {
    
    @Bean
    WorkflowExecutionCoordinatorService coordinator(
            TaskRunRepository taskRunRepository,
            TaskHandlerRegistry handlerRegistry,
            WorkflowRepository workflowRepository,
            ClockPort clock) {
        return new WorkflowExecutionCoordinatorService(...);
    }
    
    @Bean
    WorkflowRunEventListener eventListener(ExecuteWorkflowRunUseCase useCase) {
        return new WorkflowRunEventListener(useCase);
    }
    
    @Bean
    TaskHandlerRegistry handlerRegistry(List<TaskHandler> handlers) {
        TaskHandlerRegistry registry = new TaskHandlerRegistry();
        handlers.forEach(registry::register);
        return registry;
    }
}
```

### Trigger 更新

`TriggerWorkflowRunService` 需要注入 `EventPublisherPort`，在 trigger 成功后发布事件：

```java
public WorkflowRunId trigger(WorkflowId workflowId) {
    // ... 现有逻辑 ...
    WorkflowRun saved = workflowRunRepository.save(workflowRun, taskRuns);
    
    // 发布事件
    eventPublisher.publish(new WorkflowRunCreatedEvent(saved.id(), workflowId));
    
    return saved.id();
}
```

## 测试策略

### Unit Tests

- `WorkflowExecutionCoordinatorService`: DAG 依赖解析、状态更新逻辑
- `RetryService`: 重试逻辑
- `HttpTaskHandler`: HTTP 请求构造和响应处理
- `ShellTaskHandler`: Shell 命令执行

### Integration Tests

- `WorkerIntegrationTest`: 完整流程（trigger → execute → complete）
- 使用 in-memory repository

### Handler Tests

- HTTP handler: Mock HTTP server
- Shell handler: Mock process execution
- Mock handler: 验证延迟和输出

## 风险与缓解

### 风险：循环依赖

**缓解**: DAG 已在创建时验证无环，运行时直接利用 `topologicalTaskIds`

### 风险：Task 执行时间过长阻塞 Worker

**缓解**: MVP 单线程执行，后续可优化为线程池

### 风险：重试导致无限循环

**缓解**: maxRetries 限制 + 固定间隔

### 风险：数据库更新并发

**缓解**: 单节点 MVP 暂无并发问题

## 验证命令

```bash
mvn -f backend/pom.xml validate
mvn -f backend/pom.xml compile
mvn -f backend/pom.xml test
```

## 接受标准

1. POST trigger 后 Worker 自动开始执行（事件驱动）
2. Task 按 DAG 拓扑序执行，依赖的 Task 先完成
3. 执行中状态正确更新：PENDING → RUNNING → SUCCEEDED/FAILED
4. 全部 Task 完成后 WorkflowRun 状态为 SUCCEEDED
5. Task 失败自动重试（最多 3 次）
6. HTTP/Shell/Mock handler 按 type 分发执行
7. 所有非容器测试通过