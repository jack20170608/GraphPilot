"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import * as runsApi from "../lib/api/workflow-runs";

const TERMINAL_RUN_STATUSES = new Set(["SUCCEEDED", "FAILED", "CANCELED"]);
const POLL_INTERVAL_MS = 1500;

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
    // The worker executes the run asynchronously; poll until it reaches a
    // terminal status so the UI reflects live progress.
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status && TERMINAL_RUN_STATUSES.has(status) ? false : POLL_INTERVAL_MS;
    },
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

export function useTaskRuns(runId: string, isRunning: boolean) {
  return useQuery({
    queryKey: ["task-runs", runId],
    queryFn: () => runsApi.listTaskRuns(runId),
    enabled: !!runId,
    // Only poll task runs while the parent run is still in progress.
    refetchInterval: isRunning ? POLL_INTERVAL_MS : false,
  });
}

export function useWorkflowRunTimeline(runId: string, isRunning: boolean, limit = 200) {
  return useQuery({
    queryKey: ["workflow-run-timeline", runId, limit],
    queryFn: () => runsApi.listTimelineEvents(runId, limit),
    enabled: !!runId,
    refetchInterval: isRunning ? POLL_INTERVAL_MS : false,
  });
}
