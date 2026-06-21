package com.graphpilot.domain.dag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

public final class DagDefinition {

    private final Map<TaskId, TaskDefinition> tasksById;
    private final List<DagEdge> edges;
    private final List<TaskId> topologicalTaskIds;

    public DagDefinition(Collection<TaskDefinition> tasks, Collection<DagEdge> edges) {
        Objects.requireNonNull(tasks, "tasks must not be null");
        Objects.requireNonNull(edges, "edges must not be null");
        if (tasks.isEmpty()) {
            throw new DagValidationException("DAG must contain at least one task");
        }

        this.tasksById = copyTasksById(tasks);
        this.edges = List.copyOf(edges);
        validateEdges(this.edges);
        this.topologicalTaskIds = List.copyOf(sortTopologically());
    }

    public List<TaskDefinition> tasks() {
        return List.copyOf(tasksById.values());
    }

    public List<DagEdge> edges() {
        return edges;
    }

    public List<TaskId> topologicalTaskIds() {
        return topologicalTaskIds;
    }

    private Map<TaskId, TaskDefinition> copyTasksById(Collection<TaskDefinition> tasks) {
        Map<TaskId, TaskDefinition> copiedTasks = new LinkedHashMap<>();
        for (TaskDefinition task : tasks) {
            Objects.requireNonNull(task, "task must not be null");
            TaskDefinition previous = copiedTasks.putIfAbsent(task.id(), task);
            if (previous != null) {
                throw new DagValidationException("DAG contains duplicate task id: " + task.id());
            }
        }
        return Collections.unmodifiableMap(copiedTasks);
    }

    private void validateEdges(Collection<DagEdge> edges) {
        Set<DagEdge> seenEdges = new HashSet<>();
        for (DagEdge edge : edges) {
            Objects.requireNonNull(edge, "edge must not be null");
            if (!tasksById.containsKey(edge.fromTaskId())) {
                throw new DagValidationException(
                        "DAG edge references unknown source task id: " + edge.fromTaskId());
            }
            if (!tasksById.containsKey(edge.toTaskId())) {
                throw new DagValidationException(
                        "DAG edge references unknown target task id: " + edge.toTaskId());
            }
            if (!seenEdges.add(edge)) {
                throw new DagValidationException(
                        "DAG contains duplicate edge: " + edge.fromTaskId() + " -> " + edge.toTaskId());
            }
        }
    }

    private List<TaskId> sortTopologically() {
        Map<TaskId, Integer> incomingCounts = new HashMap<>();
        Map<TaskId, List<TaskId>> outgoingTaskIds = new HashMap<>();
        for (TaskId taskId : tasksById.keySet()) {
            incomingCounts.put(taskId, 0);
            outgoingTaskIds.put(taskId, new ArrayList<>());
        }

        for (DagEdge edge : edges) {
            outgoingTaskIds.get(edge.fromTaskId()).add(edge.toTaskId());
            incomingCounts.put(edge.toTaskId(), incomingCounts.get(edge.toTaskId()) + 1);
        }

        PriorityQueue<TaskId> readyTaskIds = new PriorityQueue<>();
        for (Map.Entry<TaskId, Integer> entry : incomingCounts.entrySet()) {
            if (entry.getValue() == 0) {
                readyTaskIds.add(entry.getKey());
            }
        }

        ArrayDeque<TaskId> sortedTaskIds = new ArrayDeque<>();
        while (!readyTaskIds.isEmpty()) {
            TaskId taskId = readyTaskIds.poll();
            sortedTaskIds.add(taskId);

            for (TaskId downstreamTaskId : outgoingTaskIds.get(taskId)) {
                int incomingCount = incomingCounts.get(downstreamTaskId) - 1;
                incomingCounts.put(downstreamTaskId, incomingCount);
                if (incomingCount == 0) {
                    readyTaskIds.add(downstreamTaskId);
                }
            }
        }

        if (sortedTaskIds.size() != tasksById.size()) {
            throw new DagValidationException("DAG contains a cycle");
        }
        return List.copyOf(sortedTaskIds);
    }
}
