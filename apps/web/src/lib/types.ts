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

export interface WorkflowTask {
  id: string;
  name: string;
}

export interface WorkflowEdge {
  fromTaskId: string;
  toTaskId: string;
}

export interface Workflow {
  id: string;
  name: string;
  status: WorkflowStatus;
  tasks: WorkflowTask[];
  edges: WorkflowEdge[];
  createdAt: string;
}

export interface CreateWorkflowRequest {
  name: string;
  tasks: WorkflowTask[];
  edges: WorkflowEdge[];
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

export interface TaskRun {
  id: string;
  workflowRunId: string;
  taskId: string;
  taskName: string;
  status: TaskRunStatus;
  position: number;
  createdAt: string;
}
