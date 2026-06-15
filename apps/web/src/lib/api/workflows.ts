import { request, buildQueryString } from "./client";
import type {
  Workflow,
  CreateWorkflowRequest,
  CreateWorkflowResponse,
} from "../types";

export function listWorkflows(limit = 50): Promise<Workflow[]> {
  return request<Workflow[]>(`/api/workflows${buildQueryString({ limit })}`);
}

export function getWorkflow(id: string): Promise<Workflow> {
  return request<Workflow>(`/api/workflows/${encodeURIComponent(id)}`);
}

export function createWorkflow(
  body: CreateWorkflowRequest,
): Promise<CreateWorkflowResponse> {
  return request<CreateWorkflowResponse>("/api/workflows", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function activateWorkflow(id: string): Promise<Workflow> {
  return request<Workflow>(
    `/api/workflows/${encodeURIComponent(id)}/activate`,
    { method: "POST" },
  );
}

export function pauseWorkflow(id: string): Promise<Workflow> {
  return request<Workflow>(
    `/api/workflows/${encodeURIComponent(id)}/pause`,
    { method: "POST" },
  );
}

export function resumeWorkflow(id: string): Promise<Workflow> {
  return request<Workflow>(
    `/api/workflows/${encodeURIComponent(id)}/resume`,
    { method: "POST" },
  );
}

export function archiveWorkflow(id: string): Promise<Workflow> {
  return request<Workflow>(
    `/api/workflows/${encodeURIComponent(id)}/archive`,
    { method: "POST" },
  );
}
