package com.graphpilot.adapter.worker.http;

import com.graphpilot.adapter.worker.dto.TaskExecutionRequest;
import com.graphpilot.adapter.worker.dto.TaskExecutionResponse;
import com.graphpilot.adapter.worker.http.dto.TaskExecutionMappers;
import com.graphpilot.application.execution.port.in.TaskHandler;
import com.graphpilot.application.execution.port.in.TaskHandlerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for remote task execution.
 * Exposed by the worker process to receive task dispatch requests from the scheduler.
 */
@RestController
@RequestMapping("/api/worker")
public class WorkerTaskController {

    private static final Logger log = LoggerFactory.getLogger(WorkerTaskController.class);

    private final TaskHandlerProvider taskHandlerProvider;

    public WorkerTaskController(TaskHandlerProvider taskHandlerProvider) {
        this.taskHandlerProvider = taskHandlerProvider;
    }

    /**
     * Execute a task remotely.
     * Called by the scheduler (in remote mode) to dispatch a task to this worker.
     */
    @PostMapping("/execute")
    public ResponseEntity<TaskExecutionResponse> execute(@RequestBody TaskExecutionRequest request) {
        log.info("Received task execution request: taskType={}, taskId={}",
                request.taskRun().taskType(), request.taskRun().taskId());

        TaskHandler handler;
        try {
            handler = taskHandlerProvider.getHandler(request.taskRun().taskType());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown task type: {}", request.taskRun().taskType());
            return ResponseEntity.badRequest()
                    .body(new TaskExecutionResponse("FAILED", null, "UNKNOWN_TYPE", e.getMessage()));
        }

        try {
            var result = handler.execute(
                    request.taskRun(),
                    request.taskDefinition(),
                    request.config());
            var response = TaskExecutionMappers.toResponse(result);
            log.info("Task execution completed: status={}, taskId={}",
                    response.status(), request.taskRun().taskId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Task execution failed: taskId={}, error={}",
                    request.taskRun().taskId(), e.getMessage(), e);
            return ResponseEntity.ok()
                    .body(new TaskExecutionResponse("FAILED", null, e.getClass().getSimpleName(), e.getMessage()));
        }
    }
}