# GraphPilot Task Config Expressions Design

## 状态

已批准进入计划阶段。

## 背景

GraphPilot 当前已支持：

- `TaskDefinition.config` 静态 JSON 配置。
- Worker handlers 从 task config 读取输入。
- `TaskRun.output` 字符串持久化。
- Workflow run timeline 可记录执行状态事件。

下一步要让 DAG 任务之间形成数据传递闭环：下游 task 的 config 可以引用上游 task 的 output。第一版选择轻量自研 JSONPath 子集，不引入完整表达式语言。

## 目标与非目标

### 目标

- 在 task config 的任意嵌套字符串值中支持 `${tasks.<taskId>.output...}` 表达式。
- 表达式可从上游 `TaskRun.output` 中读取完整字符串或 JSON 子路径。
- 表达式解析发生在 application 层，handler 接收解析后的最终 config。
- 表达式解析失败时，当前 task 终态失败，不调用 handler，不进入 retry。
- 保持 domain/application framework-free。

### 非目标

- 不支持引用 `status/errorMessage/finishedAt` 等 output 以外字段。
- 不支持 workflow/run/secret/env 变量。
- 不支持完整 JSONPath filter、wildcard、recursive descent、function。
- 不改变 `TaskRun.output` 的存储模型；仍为字符串。
- 不引入表达式 AST、脚本语言或第三方 JSONPath 库。

## 表达式语法

支持的表达式形态：

```text
${tasks.<taskId>.output}
${tasks.<taskId>.output.<field>}
${tasks.<taskId>.output.<field>[0].<nested>}
```

支持路径操作：

- object field：`.data`
- array index：`[0]`
- 组合访问：`.data.items[0].id`

示例：

```json
{
  "url": "https://api.example.com/items/${tasks.extract.output.itemId}",
  "body": {
    "source": "${tasks.extract.output.source}",
    "firstItem": "${tasks.extract.output.items[0].id}"
  }
}
```

非法示例：

```text
${task.extract.output}
${tasks.extract}
${tasks.extract.output.items[]}
${tasks.extract.output.items[*]}
${tasks.extract.output..id}
${tasks.extract.output.length()}
```

## 解析位置与数据流

表达式解析在 `WorkflowExecutionCoordinatorService` 调用 handler 前完成：

```text
WorkflowExecutionCoordinatorService
  -> 找 runnable task
  -> TaskConfigExpressionResolver.resolve(taskDefinition.config(), workflowRunId)
  -> handler.execute(taskRun, taskDefinition, resolvedConfig.asMap())
```

新增 application service：

```java
public final class TaskConfigExpressionResolver {
    private final WorkflowRunRepository workflowRunRepository;

    public TaskConfig resolve(TaskConfig config, WorkflowRunId workflowRunId);
}
```

解析器通过 `WorkflowRunRepository.findTaskRunsByRunId(workflowRunId)` 获取当前 run 的 task runs，然后按 taskId 查找上游结果。

解析规则：

1. 引用的上游 task 必须存在。
2. 上游 task 必须是 `SUCCEEDED`。
3. 上游 task `output` 必须非 null。
4. `${tasks.extract.output}` 直接返回 output 原始字符串。
5. `${tasks.extract.output.foo}` 会尝试把 output 解析为 JSON，再访问路径。
6. 同一次 `resolve()` 内只读取一次 task runs，并复用解析结果。
7. 不跨 task 缓存，避免状态陈旧。

## 递归替换与类型转换

解析器递归遍历 `TaskConfig` 的 JSON-like 树：

- `Map<String, Object>`：递归处理每个 value。
- `List<?>`：递归处理每个元素。
- `String`：查找并替换 `${...}` 表达式。
- `Number / Boolean / null`：原样保留。

### 整值表达式

如果整个字符串就是一个表达式：

```json
{ "timeout": "${tasks.extract.output.timeout}" }
```

解析结果保持原始 JSON 类型：

- string → string
- number → number
- boolean → boolean
- object → map
- array → list

这样 handler 可以收到 number/boolean，而不是永远收到字符串。

### 字符串片段表达式

如果表达式只是字符串的一部分：

```json
{ "command": "echo ${tasks.extract.output.id}" }
```

表达式结果转为 string 后拼接。如果结果是 object/list，则序列化为 JSON string。

### 多表达式

```json
{ "body": "id=${tasks.extract.output.id}, name=${tasks.extract.output.name}" }
```

按出现顺序逐个替换。

## JSON 输出解析

`TaskRun.output` 继续作为 string 存储。

- 表达式只到 `.output`：直接使用原始 output string。
- 表达式继续访问子路径：尝试 JSON parse output。
- JSON parse 失败：表达式失败。
- JSON path 从 parse 后 root 开始。

例如 output：

```json
{"items":[{"id":"a"}]}
```

表达式：

```text
${tasks.extract.output.items[0].id}
```

结果：

```text
a
```

## 错误处理

新增异常：

```java
public final class TaskConfigExpressionException extends RuntimeException {
    public TaskConfigExpressionException(String message) { ... }
}
```

以下情况抛出该异常：

- 表达式语法非法。
- 引用 task 不存在。
- 引用 task 非 `SUCCEEDED`。
- 引用 task output 为空。
- 访问子路径时 output 不是合法 JSON。
- JSON path 不存在。
- 数组下标越界。

`WorkflowExecutionCoordinatorService` 捕获该异常后：

- 当前 task 标记为 `FAILED`。
- `errorMessage = "Task config expression failed: ..."`。
- `output = null`。
- `finishedAt = now`。
- 不调用 handler。
- 不进入 retry。
- 写 `TASK_FAILED` timeline，message 为 `Task <id> failed while resolving config`。
- 下游 task 按现有 skip cascade 变 `SKIPPED`。
- run 最终变 `FAILED`。

解析失败不记录 `TASK_STARTED`，因为 handler 尚未开始执行。

## Mock Handler 扩展

为了稳定测试表达式链路，`mock` handler 增加可选 `output` config：

```json
{
  "type": "mock",
  "config": {
    "success": true,
    "delayMs": 0,
    "output": "{\"id\":\"abc\"}"
  }
}
```

当 `success=true` 且 `output` 存在时，handler 返回该 output；否则返回当前默认成功输出。

## 测试计划

### Resolver 单元测试

覆盖：

- `${tasks.extract.output}` 返回原始字符串。
- JSON object field：`${tasks.extract.output.id}`。
- JSON nested field：`${tasks.extract.output.data.id}`。
- JSON array index：`${tasks.extract.output.items[0].id}`。
- 整值表达式返回 string/number/boolean/map/list。
- 字符串片段替换。
- 多表达式替换。
- map/list 递归替换。
- 非字符串 config 值不处理。
- 上游 task 不存在时失败。
- 上游 task 非 `SUCCEEDED` 时失败。
- output 为 null 时失败。
- output 非 JSON 且访问子路径时失败。
- path 不存在时失败。
- array index 越界时失败。
- 表达式语法非法时失败。

### Coordinator 测试

覆盖：

- handler 收到 resolved config。
- 表达式解析失败时 handler 不被调用。
- 当前 task terminal FAILED，retryCount 不增加。
- errorMessage 包含 `Task config expression failed`。
- 下游 task SKIPPED。
- run 最终 FAILED。
- timeline 包含 `TASK_FAILED` 与 `RUN_FAILED`。

### Integration / E2E

新增 Spring 或 Micronaut E2E：

1. 创建 workflow：
   - `extract` mock output 为 JSON。
   - `load` config 使用 `${tasks.extract.output.id}`。
2. 触发 run。
3. 等待 `SUCCEEDED`。
4. 查询 task runs：
   - `extract.output` 为 JSON。
   - `load.output` 证明 handler 收到了解析后的值。
5. 查询 timeline，包含 task success 事件。

## 交付标准

- 下游 task config 可以引用上游 task output JSON 子字段。
- 支持递归 config 替换与整值表达式类型保留。
- 解析失败行为明确且不触发 retry。
- mock handler 支持 deterministic output config。
- 后端全量测试通过。
- 文档补充表达式语法。
