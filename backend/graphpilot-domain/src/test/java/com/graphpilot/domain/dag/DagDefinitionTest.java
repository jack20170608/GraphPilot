package com.graphpilot.domain.dag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class DagDefinitionTest {

    @Test
    void rejectsEmptyTaskList() {
        DagValidationException exception = assertThrows(
                DagValidationException.class,
                () -> new DagDefinition(List.of(), List.of()));

        assertEquals("DAG must contain at least one task", exception.getMessage());
    }

    @Test
    void rejectsDuplicateTaskIds() {
        TaskDefinition first = new TaskDefinition(TaskId.of("extract"), "Extract data");
        TaskDefinition duplicate = new TaskDefinition(TaskId.of("extract"), "Extract again");

        DagValidationException exception = assertThrows(
                DagValidationException.class,
                () -> new DagDefinition(List.of(first, duplicate), List.of()));

        assertEquals("DAG contains duplicate task id: extract", exception.getMessage());
    }

    @Test
    void rejectsEdgesThatReferenceUnknownTasks() {
        TaskDefinition extract = new TaskDefinition(TaskId.of("extract"), "Extract data");
        DagEdge edge = new DagEdge(TaskId.of("extract"), TaskId.of("load"));

        DagValidationException exception = assertThrows(
                DagValidationException.class,
                () -> new DagDefinition(List.of(extract), List.of(edge)));

        assertEquals("DAG edge references unknown target task id: load", exception.getMessage());
    }

    @Test
    void rejectsSelfEdges() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DagEdge(TaskId.of("extract"), TaskId.of("extract")));
    }

    @Test
    void rejectsDuplicateEdges() {
        TaskDefinition extract = new TaskDefinition(TaskId.of("extract"), "Extract data");
        TaskDefinition load = new TaskDefinition(TaskId.of("load"), "Load data");
        DagEdge edge = new DagEdge(TaskId.of("extract"), TaskId.of("load"));

        DagValidationException exception = assertThrows(
                DagValidationException.class,
                () -> new DagDefinition(List.of(extract, load), List.of(edge, edge)));

        assertEquals("DAG contains duplicate edge: extract -> load", exception.getMessage());
    }

    @Test
    void returnsSingleTaskForSingleNodeDag() {
        TaskDefinition extract = new TaskDefinition(TaskId.of("extract"), "Extract data");

        DagDefinition dag = new DagDefinition(List.of(extract), List.of());

        assertEquals(List.of(TaskId.of("extract")), dag.topologicalTaskIds());
    }

    @Test
    void returnsDeterministicOrderForMultipleSources() {
        TaskDefinition beta = new TaskDefinition(TaskId.of("beta"), "Beta task");
        TaskDefinition alpha = new TaskDefinition(TaskId.of("alpha"), "Alpha task");
        TaskDefinition gamma = new TaskDefinition(TaskId.of("gamma"), "Gamma task");

        DagDefinition dag = new DagDefinition(List.of(gamma, beta, alpha), List.of());

        assertEquals(
                List.of(TaskId.of("alpha"), TaskId.of("beta"), TaskId.of("gamma")),
                dag.topologicalTaskIds());
    }

    @Test
    void rejectsCycles() {
        TaskDefinition extract = new TaskDefinition(TaskId.of("extract"), "Extract data");
        TaskDefinition transform = new TaskDefinition(TaskId.of("transform"), "Transform data");

        DagValidationException exception = assertThrows(
                DagValidationException.class,
                () -> new DagDefinition(
                        List.of(extract, transform),
                        List.of(
                                new DagEdge(TaskId.of("extract"), TaskId.of("transform")),
                                new DagEdge(TaskId.of("transform"), TaskId.of("extract")))));

        assertEquals("DAG contains a cycle", exception.getMessage());
    }

    @Test
    void returnsTopologicalOrderForChain() {
        TaskDefinition extract = new TaskDefinition(TaskId.of("extract"), "Extract data");
        TaskDefinition transform = new TaskDefinition(TaskId.of("transform"), "Transform data");
        TaskDefinition load = new TaskDefinition(TaskId.of("load"), "Load data");

        DagDefinition dag = new DagDefinition(
                List.of(load, transform, extract),
                List.of(
                        new DagEdge(TaskId.of("extract"), TaskId.of("transform")),
                        new DagEdge(TaskId.of("transform"), TaskId.of("load"))));

        assertEquals(
                List.of(TaskId.of("extract"), TaskId.of("transform"), TaskId.of("load")),
                dag.topologicalTaskIds());
    }

    @Test
    void returnsTopologicalOrderForDiamond() {
        TaskDefinition start = new TaskDefinition(TaskId.of("start"), "Start");
        TaskDefinition left = new TaskDefinition(TaskId.of("left"), "Left branch");
        TaskDefinition right = new TaskDefinition(TaskId.of("right"), "Right branch");
        TaskDefinition finish = new TaskDefinition(TaskId.of("finish"), "Finish");

        DagDefinition dag = new DagDefinition(
                List.of(finish, right, left, start),
                List.of(
                        new DagEdge(TaskId.of("start"), TaskId.of("left")),
                        new DagEdge(TaskId.of("start"), TaskId.of("right")),
                        new DagEdge(TaskId.of("left"), TaskId.of("finish")),
                        new DagEdge(TaskId.of("right"), TaskId.of("finish"))));

        List<TaskId> sorted = dag.topologicalTaskIds();

        assertEquals(
                List.of(TaskId.of("start"), TaskId.of("left"), TaskId.of("right"), TaskId.of("finish")),
                sorted);
    }
}
