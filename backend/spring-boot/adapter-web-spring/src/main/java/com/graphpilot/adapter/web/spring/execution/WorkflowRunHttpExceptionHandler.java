package com.graphpilot.adapter.web.spring.execution;

import com.graphpilot.scheduler.application.execution.WorkflowRunNotFoundException;
import com.graphpilot.application.shared.exception.WorkflowNotFoundException;
import com.graphpilot.domain.execution.WorkflowRunTriggerException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = WorkflowRunController.class)
class WorkflowRunHttpExceptionHandler {

    @ExceptionHandler({WorkflowRunNotFoundException.class, WorkflowNotFoundException.class})
    ProblemDetail handleWorkflowRunNotFound(RuntimeException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problemDetail.setTitle("Workflow run not found");
        problemDetail.setDetail("Workflow run was not found");
        return problemDetail;
    }

    @ExceptionHandler(WorkflowRunTriggerException.class)
    ProblemDetail handleWorkflowRunTriggerFailure(WorkflowRunTriggerException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problemDetail.setTitle("Workflow run cannot be triggered");
        problemDetail.setDetail(exception.getMessage());
        return problemDetail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalidWorkflowRunRequest(IllegalArgumentException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Invalid workflow run request");
        problemDetail.setDetail(exception.getMessage());
        return problemDetail;
    }
}
