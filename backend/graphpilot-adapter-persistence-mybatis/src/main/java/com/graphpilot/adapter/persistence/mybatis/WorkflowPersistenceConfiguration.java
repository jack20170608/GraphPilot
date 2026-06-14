package com.graphpilot.adapter.persistence.mybatis;

import com.graphpilot.adapter.persistence.mybatis.mapper.WorkflowMapper;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
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
}
