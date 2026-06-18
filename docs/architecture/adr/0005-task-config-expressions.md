# ADR 0005: Task Config Expressions

## 状态

已接受

## 背景

DAG 任务经常需要消费上游任务的输出。例如，一个 "load" 任务需要把 "extract" 任务返回的 JSON 中的某个字段作为输入。在 GraphPilot 中，任务的输入配置是静态的 `TaskConfig`（`Map<String, Object>`），在编排时无法动态引用前置任务的执行结果。

我们需要一种简洁、可扩展的机制，让下游任务在运行时能够引用上游任务的输出，同时保持领域模型的框架中立性。

## 决策

引入 **Task Config Expression** 机制：在 `TaskConfig` 的字符串值中使用 `${tasks.<taskId>.output<path>}` 语法，运行时由 `TaskConfigExpressionResolver` 解析并替换为上游任务的实际输出。

### 语法设计

```text
${tasks.<taskId>.output}           # 引用上游任务的原始输出字符串
${tasks.<taskId>.output.id}        # 引用上游任务 JSON 输出中的字段
${tasks.<taskId>.output.items[0]}  # 引用 JSON 数组元素
${tasks.<taskId>.output.matrix[1][0]} # 支持连续数组索引
```

### 解析规则

1. **全量表达式**（`wholeMatcher.matches()`）：当整个字符串就是一个表达式时，保留解析结果的原始类型（`String`、`Number`、`Boolean`、`Map`、`List`、`null`）。
2. **内嵌表达式**（`matcher.find()`）：当表达式嵌入在普通文本中时，解析结果被字符串化后替换到原位置。复杂对象（`Map`/`List`）使用 JSON 序列化；`null` 被替换为 `"null"` 字面量。
3. **递归解析**：`Map` 和 `List` 中的值会被递归解析。
4. **非字符串值不变**：`Number`、`Boolean` 等非字符串类型直接透传，不做处理。

### 错误处理

解析失败时抛出 `TaskConfigExpressionException`（非受检异常），包含明确的错误信息：

- 引用的任务不存在
- 引用的任务未成功完成
- 上游任务输出为空
- 上游任务输出不是合法 JSON 但路径要求解析 JSON
- 路径不存在或数组越界
- 表达式语法错误

`WorkflowExecutionCoordinatorService` 在调用 `expressionResolver.resolve()` 时捕获 `RuntimeException`（包括 `TaskConfigExpressionException` 和其他不可预期的运行时异常），将任务直接标记为 **FAILED**（不触发重试），错误信息前缀为 `Task config expression failed:`。

### 架构边界

- `TaskConfigExpressionResolver` 位于 `graphpilot-application`，依赖 `WorkflowRunRepository`（出站端口）和 `JsonValueCodecPort`（新引入的出站端口）。
- `JsonValueCodecPort` 是框架中立的 JSON 编解码端口，由 `graphpilot-adapter-worker` 中的 `JacksonJsonValueCodec` 实现。
- `graphpilot-application` 编译时**不依赖 Jackson**，仅在测试范围使用 Jackson 实现测试 Fake。

## 影响

- 下游任务可以灵活消费上游输出，无需在领域层引入复杂的动态类型系统。
- 表达式解析发生在任务执行前（`executeTask` 阶段），失败时任务直接进入 FAILED 状态，不会调用 TaskHandler，避免把解析错误传播到 Handler。
- `JsonValueCodecPort` 的引入确保 application 模块保持框架中立；未来可以替换为其他 JSON 库（如 Gson、JSON-B）而不影响应用层代码。
- 表达式语法简单直观，支持嵌套路径和数组索引，覆盖绝大多数 JSON 输出引用场景。
- 当前实现仅支持 `tasks.*.output` 表达式；未来可以扩展支持其他上下文（如全局变量、工作流输入等）。
