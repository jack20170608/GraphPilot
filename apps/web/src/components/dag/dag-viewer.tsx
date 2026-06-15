"use client";

import { useMemo, useCallback } from "react";
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  type Node,
  type Edge,
  type NodeTypes,
  Position,
  MarkerType,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import type { TaskDefinition, DagEdge, TaskRunStatus } from "@/lib/types";

interface DagViewerProps {
  tasks: TaskDefinition[];
  edges: DagEdge[];
  taskStatuses?: Record<string, TaskRunStatus>;
}

const statusColors: Record<TaskRunStatus, string> = {
  PENDING: "#94a3b8",
  RUNNING: "#3b82f6",
  SUCCEEDED: "#22c55e",
  FAILED: "#ef4444",
  SKIPPED: "#a1a1aa",
};

function TaskNode({ data }: { data: { label: string; status?: TaskRunStatus } }) {
  const borderColor = data.status ? statusColors[data.status] : "#e2e8f0";
  return (
    <div
      className="px-4 py-2 rounded-lg border-2 bg-card shadow-sm text-sm font-medium"
      style={{ borderColor }}
    >
      {data.label}
    </div>
  );
}

const nodeTypes: NodeTypes = {
  task: TaskNode,
};

export function DagViewer({ tasks, edges, taskStatuses }: DagViewerProps) {
  const nodes: Node[] = useMemo(() => {
    const positioned = autoLayout(tasks, edges);
    return positioned.map((t) => ({
      id: t.id,
      type: "task",
      position: { x: t.x, y: t.y },
      data: { label: t.name, status: taskStatuses?.[t.id] },
    }));
  }, [tasks, edges, taskStatuses]);

  const rfEdges: Edge[] = useMemo(
    () =>
      edges.map((e, i) => ({
        id: `e-${i}`,
        source: e.fromTaskId,
        target: e.toTaskId,
        markerEnd: { type: MarkerType.ArrowClosed },
        animated: taskStatuses?.[e.fromTaskId] === "RUNNING" || taskStatuses?.[e.toTaskId] === "RUNNING",
      })),
    [edges, taskStatuses],
  );

  const defaultEdgeOptions = useMemo(
    () => ({ type: "smoothstep" as const }),
    [],
  );

  return (
    <div className="h-[600px] w-full rounded-lg border bg-background">
      <ReactFlow
        nodes={nodes}
        edges={rfEdges}
        nodeTypes={nodeTypes}
        defaultEdgeOptions={defaultEdgeOptions}
        fitView
        minZoom={0.3}
      >
        <Background />
        <Controls />
        <MiniMap />
      </ReactFlow>
    </div>
  );
}

interface LayoutNode {
  id: string;
  name: string;
  x: number;
  y: number;
}

function autoLayout(tasks: TaskDefinition[], edges: DagEdge[]): LayoutNode[] {
  const nodeWidth = 180;
  const nodeHeight = 60;
  const xGap = 80;
  const yGap = 60;

  const inDegree = new Map<string, number>();
  const adjacency = new Map<string, string[]>();

  for (const t of tasks) {
    inDegree.set(t.id, 0);
    adjacency.set(t.id, []);
  }
  for (const e of edges) {
    inDegree.set(e.toTaskId, (inDegree.get(e.toTaskId) ?? 0) + 1);
    adjacency.get(e.fromTaskId)?.push(e.toTaskId);
  }

  const layers: string[][] = [];
  const queue: string[] = [];
  const visited = new Set<string>();

  for (const [id, deg] of inDegree) {
    if (deg === 0) queue.push(id);
  }

  while (queue.length > 0) {
    const layer = [...queue];
    layers.push(layer);
    const nextQueue: string[] = [];
    for (const id of layer) {
      visited.add(id);
      for (const child of adjacency.get(id) ?? []) {
        const newDeg = (inDegree.get(child) ?? 1) - 1;
        inDegree.set(child, newDeg);
        if (newDeg === 0 && !visited.has(child)) {
          nextQueue.push(child);
        }
      }
    }
    queue.length = 0;
    queue.push(...nextQueue);
  }

  for (const t of tasks) {
    if (!visited.has(t.id)) {
      layers.push([t.id]);
    }
  }

  const taskMap = new Map(tasks.map((t) => [t.id, t]));
  const result: LayoutNode[] = [];

  for (let li = 0; li < layers.length; li++) {
    const layer = layers[li];
    const totalWidth = layer.length * nodeWidth + (layer.length - 1) * xGap;
    const startX = -totalWidth / 2;
    for (let ni = 0; ni < layer.length; ni++) {
      const id = layer[ni];
      result.push({
        id,
        name: taskMap.get(id)?.name ?? id,
        x: startX + ni * (nodeWidth + xGap),
        y: li * (nodeHeight + yGap),
      });
    }
  }

  return result;
}
