package com.graphpilot.adapter.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.graphpilot.domain.workflow.WorkflowId;
import org.junit.jupiter.api.Test;

class UuidWorkflowIdGeneratorTest {

    private final UuidWorkflowIdGenerator idGenerator = new UuidWorkflowIdGenerator();

    @Test
    void generatesUniqueWorkflowIds() {
        WorkflowId firstWorkflowId = idGenerator.nextWorkflowId();
        WorkflowId secondWorkflowId = idGenerator.nextWorkflowId();

        assertThat(firstWorkflowId.value()).isNotBlank();
        assertThat(secondWorkflowId.value()).isNotBlank();
        assertThat(firstWorkflowId).isNotEqualTo(secondWorkflowId);
    }
}
