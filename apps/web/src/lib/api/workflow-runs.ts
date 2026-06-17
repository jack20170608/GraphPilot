import { request, buildQueryString } from "./client";
import type {
  WorkflowRun,
  TaskRun,
  CreateWorkflowRunResponse,
  WorkflowRunTimelineEvent,
} from "../types";

export function listWorkflowRuns(
  workflowId: string,
  limit = 50,
): Promise<WorkflowRun[]> {
  return request<WorkflowRun[]>(
    `/api/workflows/${encodeURIComponent(workflowId)}/runs${buildQueryString({ limit })}`,
  );
}

export function getWorkflowRun(runId: string): Promise<WorkflowRun> {
  return request<WorkflowRun>(
    `/api/workflow-runs/${encodeURIComponent(runId)}`,
  );
}

export function triggerWorkflowRun(
  workflowId: string,
): Promise<CreateWorkflowRunResponse> {
  return request<CreateWorkflowRunResponse>(
    `/api/workflows/${encodeURIComponent(workflowId)}/runs`,
    { method: "POST" },
  );
}

export function listTaskRuns(runId: string): Promise<TaskRun[]> {
  return request<TaskRun[]>(
    `/api/workflow-runs/${encodeURIComponent(runId)}/tasks`,
  );
}

export function listTimelineEvents(
  runId: string,
  limit = 200,
): Promise<WorkflowRunTimelineEvent[]> {
  return request<WorkflowRunTimelineEvent[]>(
    `/api/workflow-runs/${encodeURIComponent(runId)}/timeline${buildQueryString({ limit })}`,
  );
}
