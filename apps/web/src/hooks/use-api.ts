import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { workflowsApi, workflowRunsApi } from "@/lib/api";

export function useWorkflows(limit = 50) {
  return useQuery({
    queryKey: ["workflows", limit],
    queryFn: () => workflowsApi.list(limit),
  });
}

export function useWorkflow(id: string) {
  return useQuery({
    queryKey: ["workflow", id],
    queryFn: () => workflowsApi.getById(id),
    enabled: !!id,
  });
}

export function useCreateWorkflow() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: workflowsApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["workflows"] });
    },
  });
}

export function useActivateWorkflow() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: workflowsApi.activate,
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: ["workflow", id] });
      queryClient.invalidateQueries({ queryKey: ["workflows"] });
    },
  });
}

export function usePauseWorkflow() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: workflowsApi.pause,
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: ["workflow", id] });
      queryClient.invalidateQueries({ queryKey: ["workflows"] });
    },
  });
}

export function useResumeWorkflow() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: workflowsApi.resume,
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: ["workflow", id] });
      queryClient.invalidateQueries({ queryKey: ["workflows"] });
    },
  });
}

export function useArchiveWorkflow() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: workflowsApi.archive,
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: ["workflow", id] });
      queryClient.invalidateQueries({ queryKey: ["workflows"] });
    },
  });
}

export function useWorkflowRuns(workflowId: string, limit = 50) {
  return useQuery({
    queryKey: ["workflow-runs", workflowId, limit],
    queryFn: () => workflowRunsApi.listByWorkflowId(workflowId, limit),
    enabled: !!workflowId,
  });
}

export function useWorkflowRun(runId: string) {
  return useQuery({
    queryKey: ["workflow-run", runId],
    queryFn: () => workflowRunsApi.getById(runId),
    enabled: !!runId,
  });
}

export function useTriggerWorkflowRun() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: workflowRunsApi.trigger,
    onSuccess: (_data, workflowId) => {
      queryClient.invalidateQueries({
        queryKey: ["workflow-runs", workflowId],
      });
    },
  });
}

export function useTaskRuns(runId: string) {
  return useQuery({
    queryKey: ["task-runs", runId],
    queryFn: () => workflowRunsApi.listTaskRuns(runId),
    enabled: !!runId,
  });
}
