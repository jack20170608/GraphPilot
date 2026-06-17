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
  type?: string;
  config?: Record<string, unknown>;
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
  tasks: Array<{ id: string; name: string; type?: string; config?: Record<string, unknown> }>;
  edges: Array<{ fromTaskId: string; toTaskId: string }>;
}

export interface CreateWorkflowResponse {
  id: string;
}

export interface WorkflowRun {
  id: string;
  workflowId: string;
  status: WorkflowRunStatus;
  triggeredAt: string;
  startedAt?: string;
  finishedAt?: string;
}

export interface CreateWorkflowRunResponse {
  id: string;
}

export type TimelineEventType =
  | "RUN_CREATED"
  | "RUN_STARTED"
  | "TASK_STARTED"
  | "TASK_SUCCEEDED"
  | "TASK_FAILED"
  | "TASK_SKIPPED"
  | "RUN_SUCCEEDED"
  | "RUN_FAILED";

export interface WorkflowRunTimelineEvent {
  id: string;
  workflowRunId: string;
  taskRunId?: string;
  taskId?: string;
  type: TimelineEventType;
  message: string;
  occurredAt: string;
}

export interface TaskRun {
  id: string;
  workflowRunId: string;
  taskId: string;
  taskName: string;
  taskType: string;
  status: TaskRunStatus;
  position: number;
  retryCount: number;
  maxRetries: number;
  errorMessage?: string;
  output?: string;
  startedAt?: string;
  finishedAt?: string;
  createdAt: string;
}
