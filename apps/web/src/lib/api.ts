import type {
  Workflow,
  WorkflowRun,
  TaskRun,
  CreateWorkflowRequest,
  CreateWorkflowResponse,
  CreateWorkflowRunResponse,
} from "@/lib/types";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const url = `${API_BASE_URL}${path}`;
  const res = await fetch(url, {
    headers: {
      "Content-Type": "application/json",
      ...init?.headers,
    },
    ...init,
  });
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(`API error ${res.status}: ${body || res.statusText}`);
  }
  if (res.status === 201) {
    const location = res.headers.get("Location");
    if (location) {
      const detailRes = await fetch(`${API_BASE_URL}${location}`);
      if (detailRes.ok) {
        return detailRes.json() as Promise<T>;
      }
    }
  }
  return res.json() as Promise<T>;
}

export const workflowsApi = {
  list(limit = 50): Promise<Workflow[]> {
    return request(`/api/workflows?limit=${limit}`);
  },
  getById(id: string): Promise<Workflow> {
    return request(`/api/workflows/${encodeURIComponent(id)}`);
  },
  create(data: CreateWorkflowRequest): Promise<CreateWorkflowResponse> {
    return request(`/api/workflows`, {
      method: "POST",
      body: JSON.stringify(data),
    });
  },
  activate(id: string): Promise<Workflow> {
    return request(`/api/workflows/${encodeURIComponent(id)}/activate`, {
      method: "POST",
    });
  },
  pause(id: string): Promise<Workflow> {
    return request(`/api/workflows/${encodeURIComponent(id)}/pause`, {
      method: "POST",
    });
  },
  resume(id: string): Promise<Workflow> {
    return request(`/api/workflows/${encodeURIComponent(id)}/resume`, {
      method: "POST",
    });
  },
  archive(id: string): Promise<Workflow> {
    return request(`/api/workflows/${encodeURIComponent(id)}/archive`, {
      method: "POST",
    });
  },
};

export const workflowRunsApi = {
  listByWorkflowId(workflowId: string, limit = 50): Promise<WorkflowRun[]> {
    return request(
      `/api/workflows/${encodeURIComponent(workflowId)}/runs?limit=${limit}`
    );
  },
  getById(runId: string): Promise<WorkflowRun> {
    return request(`/api/workflow-runs/${encodeURIComponent(runId)}`);
  },
  trigger(workflowId: string): Promise<CreateWorkflowRunResponse> {
    return request(
      `/api/workflows/${encodeURIComponent(workflowId)}/runs`,
      { method: "POST" }
    );
  },
  listTaskRuns(runId: string): Promise<TaskRun[]> {
    return request(`/api/workflow-runs/${encodeURIComponent(runId)}/tasks`);
  },
};
