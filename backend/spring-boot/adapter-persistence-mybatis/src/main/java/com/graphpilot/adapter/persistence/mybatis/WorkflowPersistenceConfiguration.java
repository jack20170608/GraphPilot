package com.graphpilot.adapter.persistence.mybatis;

import com.graphpilot.adapter.persistence.mybatis.mapper.WorkflowMapper;
import com.graphpilot.adapter.persistence.mybatis.mapper.WorkflowRunMapper;
import com.graphpilot.application.shared.port.WorkflowRepository;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("postgres")
@MapperScan(basePackageClasses = WorkflowMapper.class)
public class WorkflowPersistenceConfiguration {

    @Bean
    WorkflowRepository workflowRepository(WorkflowMapper workflowMapper) {
        return new MyBatisWorkflowRepository(workflowMapper);
    }

    @Bean
    com.graphpilot.scheduler.application.execution.port.out.WorkflowRunRepository workflowRunRepository(
            WorkflowRunMapper workflowRunMapper) {
        return new MyBatisWorkflowRunRepository(workflowRunMapper);
    }
}
