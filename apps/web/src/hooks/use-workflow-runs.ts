"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import * as runsApi from "../lib/api/workflow-runs";

export function useWorkflowRuns(workflowId: string, limit = 50) {
  return useQuery({
    queryKey: ["workflow-runs", workflowId, limit],
    queryFn: () => runsApi.listWorkflowRuns(workflowId, limit),
    enabled: !!workflowId,
  });
}

export function useWorkflowRun(runId: string) {
  return useQuery({
    queryKey: ["workflow-run", runId],
    queryFn: () => runsApi.getWorkflowRun(runId),
    enabled: !!runId,
  });
}

export function useTriggerWorkflowRun() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: runsApi.triggerWorkflowRun,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["workflow-runs"] });
    },
  });
}

export function useTaskRuns(runId: string) {
  return useQuery({
    queryKey: ["task-runs", runId],
    queryFn: () => runsApi.listTaskRuns(runId),
    enabled: !!runId,
  });
}
