package com.graphpilot.adapter.persistence.mybatis;

import com.graphpilot.adapter.persistence.mybatis.mapper.WorkflowMapper;
import com.graphpilot.adapter.persistence.mybatis.row.WorkflowEdgeRow;
import com.graphpilot.adapter.persistence.mybatis.row.WorkflowRow;
import com.graphpilot.adapter.persistence.mybatis.row.WorkflowTaskRow;
import com.graphpilot.application.workflow.port.out.WorkflowRepository;
import com.graphpilot.domain.dag.DagDefinition;
import com.graphpilot.domain.dag.DagEdge;
import com.graphpilot.domain.dag.TaskDefinition;
import com.graphpilot.domain.dag.TaskId;
import com.graphpilot.domain.workflow.Workflow;
import com.graphpilot.domain.workflow.WorkflowId;
import com.graphpilot.domain.workflow.WorkflowName;
import com.graphpilot.domain.workflow.WorkflowStatus;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

public class MyBatisWorkflowRepository implements WorkflowRepository {

    private static final String POSITIVE_LIMIT_REQUIRED = "Workflow query limit must be positive";

    private final WorkflowMapper workflowMapper;

    public MyBatisWorkflowRepository(WorkflowMapper workflowMapper) {
        this.workflowMapper = Objects.requireNonNull(workflowMapper, "workflowMapper must not be null");
    }

    @Override
    @Transactional
    public Workflow save(Workflow workflow) {
        Objects.requireNonNull(workflow, "workflow must not be null");

        String workflowId = workflow.id().value();
        workflowMapper.upsertWorkflow(toWorkflowRow(workflow));
        workflowMapper.deleteEdgesByWorkflowId(workflowId);
        workflowMapper.deleteTasksByWorkflowId(workflowId);
        List<WorkflowTaskRow> taskRows = toTaskRows(workflowId, workflow.dag().tasks());
        if (!taskRows.isEmpty()) {
            workflowMapper.insertTasks(taskRows);
        }
        List<WorkflowEdgeRow> edgeRows = toEdgeRows(workflowId, workflow.dag().edges());
        if (!edgeRows.isEmpty()) {
            workflowMapper.insertEdges(edgeRows);
        }
        return workflow;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<Workflow> findById(WorkflowId workflowId) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");

        return Optional.ofNullable(workflowMapper.findWorkflowById(workflowId.value()))
                .map(this::toWorkflow);
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Optional<Workflow> findByIdForRunTrigger(WorkflowId workflowId) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");

        return Optional.ofNullable(workflowMapper.findWorkflowByIdForUpdate(workflowId.value()))
                .map(this::toWorkflow);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Workflow> findAll(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException(POSITIVE_LIMIT_REQUIRED);
        }

        List<WorkflowRow> workflowRows = workflowMapper.findAllWorkflows(limit);
        if (workflowRows.isEmpty()) {
            return List.of();
        }

        List<String> workflowIds = workflowRows.stream()
                .map(WorkflowRow::id)
                .toList();
        Map<String, List<WorkflowTaskRow>> tasksByWorkflowId = workflowMapper.findTasksByWorkflowIds(workflowIds).stream()
                .collect(Collectors.groupingBy(WorkflowTaskRow::workflowId));
        Map<String, List<WorkflowEdgeRow>> edgesByWorkflowId = workflowMapper.findEdgesByWorkflowIds(workflowIds).stream()
                .collect(Collectors.groupingBy(WorkflowEdgeRow::workflowId));

        return workflowRows.stream()
                .map(workflowRow -> toWorkflow(
                        workflowRow,
                        tasksByWorkflowId.getOrDefault(workflowRow.id(), List.of()),
                        edgesByWorkflowId.getOrDefault(workflowRow.id(), List.of())))
                .toList();
    }

    private WorkflowRow toWorkflowRow(Workflow workflow) {
        return new WorkflowRow(
                workflow.id().value(),
                workflow.name().value(),
                workflow.status().name(),
                workflow.createdAt());
    }

    private static List<WorkflowTaskRow> toTaskRows(String workflowId, List<TaskDefinition> tasks) {
        return java.util.stream.IntStream.range(0, tasks.size())
                .mapToObj(position -> {
                    TaskDefinition task = tasks.get(position);
                    return new WorkflowTaskRow(
                            workflowId,
                            task.id().value(),
                            task.name(),
                            position);
                })
                .toList();
    }

    private static List<WorkflowEdgeRow> toEdgeRows(String workflowId, List<DagEdge> edges) {
        return java.util.stream.IntStream.range(0, edges.size())
                .mapToObj(position -> {
                    DagEdge edge = edges.get(position);
                    return new WorkflowEdgeRow(
                            workflowId,
                            edge.fromTaskId().value(),
                            edge.toTaskId().value(),
                            position);
                })
                .toList();
    }

    private Workflow toWorkflow(WorkflowRow workflowRow) {
        String workflowId = workflowRow.id();
        return toWorkflow(
                workflowRow,
                workflowMapper.findTasksByWorkflowId(workflowId),
                workflowMapper.findEdgesByWorkflowId(workflowId));
    }

    private Workflow toWorkflow(
            WorkflowRow workflowRow,
            List<WorkflowTaskRow> taskRows,
            List<WorkflowEdgeRow> edgeRows) {
        List<TaskDefinition> tasks = taskRows.stream()
                .map(this::toTaskDefinition)
                .toList();
        List<DagEdge> edges = edgeRows.stream()
                .map(this::toDagEdge)
                .toList();

        return Workflow.restore(
                WorkflowId.of(workflowRow.id()),
                WorkflowName.of(workflowRow.name()),
                new DagDefinition(tasks, edges),
                WorkflowStatus.valueOf(workflowRow.status()),
                workflowRow.createdAt());
    }

    private TaskDefinition toTaskDefinition(WorkflowTaskRow taskRow) {
        return new TaskDefinition(TaskId.of(taskRow.taskId()), taskRow.name());
    }

    private DagEdge toDagEdge(WorkflowEdgeRow edgeRow) {
        return new DagEdge(
                TaskId.of(edgeRow.sourceTaskId()),
                TaskId.of(edgeRow.targetTaskId()));
    }
}
