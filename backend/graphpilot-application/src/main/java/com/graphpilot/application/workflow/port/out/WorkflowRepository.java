package com.graphpilot.application.workflow.port.out;

import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import java.util.Optional;

public interface WorkflowRepository {

    Workflow save(Workflow workflow);

    Optional<Workflow> findById(WorkflowId workflowId);
}
