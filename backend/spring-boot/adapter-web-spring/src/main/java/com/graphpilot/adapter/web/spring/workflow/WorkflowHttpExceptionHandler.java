package com.graphpilot.adapter.web.spring.workflow;

import com.graphpilot.application.shared.exception.WorkflowNotFoundException;
import com.graphpilot.domain.dag.DagValidationException;
import com.graphpilot.domain.workflow.WorkflowLifecycleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class WorkflowHttpExceptionHandler {

    @ExceptionHandler({DagValidationException.class, IllegalArgumentException.class})
    ProblemDetail handleInvalidWorkflowRequest(RuntimeException exception) {
        return badRequestProblem("Workflow request failed validation");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleInvalidRequestBody(MethodArgumentNotValidException exception) {
        return badRequestProblem("Workflow request body is invalid");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableRequestBody(HttpMessageNotReadableException exception) {
        return badRequestProblem("Workflow request body is malformed");
    }

    @ExceptionHandler(WorkflowNotFoundException.class)
    ProblemDetail handleWorkflowNotFound(WorkflowNotFoundException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problemDetail.setTitle("Workflow not found");
        problemDetail.setDetail("Workflow was not found");
        return problemDetail;
    }

    @ExceptionHandler(WorkflowLifecycleException.class)
    ProblemDetail handleInvalidWorkflowLifecycleTransition(WorkflowLifecycleException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problemDetail.setTitle("Invalid workflow lifecycle transition");
        problemDetail.setDetail(exception.getMessage());
        return problemDetail;
    }

    private static ProblemDetail badRequestProblem(String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Invalid workflow request");
        problemDetail.setDetail(detail);
        return problemDetail;
    }
}
