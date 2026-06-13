package com.graphpilot.bootstrap.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.graphpilot.adapter.persistence.memory.InMemoryWorkflowRepository;
import com.graphpilot.application.workflow.port.in.CreateWorkflowUseCase;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GraphPilotApplicationTest {

    @Autowired
    private CreateWorkflowUseCase createWorkflowUseCase;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Test
    void loadsWorkflowBeans() {
        assertThat(createWorkflowUseCase).isNotNull();
        assertThat(workflowRepository).isInstanceOf(InMemoryWorkflowRepository.class);
    }
}
