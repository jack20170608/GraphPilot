package com.graphpilot.adapter.worker.http;

import com.graphpilot.adapter.worker.dto.TaskExecutionResponse;
import com.graphpilot.adapter.worker.http.dto.TaskExecutionMappers;
import com.graphpilot.domain.execution.TaskResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for TaskExecutionMappers - verifying DTO conversion between domain and HTTP layer.
 */
class TaskExecutionMappersTests {

    @Test
    void toResponse_success() {
        TaskResult result = TaskResult.success("output data");

        TaskExecutionResponse response = TaskExecutionMappers.toResponse(result);

        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.output()).isEqualTo("output data");
        assertThat(response.success()).isTrue();
    }

    @Test
    void toResponse_failure() {
        TaskResult result = TaskResult.failure("EXEC_ERROR", "Something went wrong");

        TaskExecutionResponse response = TaskExecutionMappers.toResponse(result);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.error()).isEqualTo("EXEC_ERROR");
        assertThat(response.errorMessage()).isEqualTo("Something went wrong");
        assertThat(response.failed()).isTrue();
    }

    @Test
    void toResponse_skipped() {
        TaskResult result = TaskResult.skipped();

        TaskExecutionResponse response = TaskExecutionMappers.toResponse(result);

        assertThat(response.status()).isEqualTo("SKIPPED");
        assertThat(response.skipped()).isTrue();
    }

    @Test
    void toTaskResult_success() {
        TaskExecutionResponse response = new TaskExecutionResponse("SUCCEEDED", "test output", null, null);

        TaskResult result = TaskExecutionMappers.toTaskResult(response);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).contains("test output");
    }

    @Test
    void toTaskResult_failure() {
        TaskExecutionResponse response = new TaskExecutionResponse("FAILED", null, "ERROR_CODE", "error message");

        TaskResult result = TaskExecutionMappers.toTaskResult(response);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).contains("ERROR_CODE");
        assertThat(result.errorMessage()).contains("error message");
    }

    @Test
    void toTaskResult_skipped() {
        TaskExecutionResponse response = new TaskExecutionResponse("SKIPPED", null, null, null);

        TaskResult result = TaskExecutionMappers.toTaskResult(response);

        assertThat(result.isFailure()).isFalse();
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void toTaskResult_unknownStatus() {
        TaskExecutionResponse response = new TaskExecutionResponse("UNKNOWN", null, null, null);

        TaskResult result = TaskExecutionMappers.toTaskResult(response);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).contains("BAD_RESPONSE");
    }
}