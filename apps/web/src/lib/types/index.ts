export type WorkflowStatus = "DRAFT" | "ACTIVE" | "PAUSED" | "ARCHIVED";

export type WorkflowRunStatus =
  | "PENDING"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELED";

export type TaskRunStatus =
  | "PENDING"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED"
  | "SKIPPED";

export interface TaskDefinition {
  id: string;
  name: string;
}

export interface DagEdge {
  fromTaskId: string;
  toTaskId: string;
}

export interface Workflow {
  id: string;
  name: string;
  status: WorkflowStatus;
  tasks: TaskDefinition[];
  edges: DagEdge[];
  createdAt: string;
}

export interface CreateWorkflowRequest {
  name: string;
  tasks: { id: string; name: string }[];
  edges: { fromTaskId: string; toTaskId: string }[];
}

export interface CreateWorkflowResponse {
  id: string;
}

export interface WorkflowRun {
  id: string;
  workflowId: string;
  status: WorkflowRunStatus;
  triggeredAt: string;
}

export interface CreateWorkflowRunResponse {
  id: string;
}

export interface TaskRun {
  id: string;
  workflowRunId: string;
  taskId: string;
  taskName: string;
  status: TaskRunStatus;
  position: number;
  createdAt: string;
}
