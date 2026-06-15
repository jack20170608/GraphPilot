import type { WorkflowStatus, WorkflowRunStatus, TaskRunStatus } from "@/lib/types";
import { Badge } from "@/components/ui/badge";

const workflowStatusConfig: Record<WorkflowStatus, { label: string; variant: "default" | "secondary" | "outline" | "destructive" }> = {
  DRAFT: { label: "草稿", variant: "outline" },
  ACTIVE: { label: "已激活", variant: "default" },
  PAUSED: { label: "已暂停", variant: "secondary" },
  ARCHIVED: { label: "已归档", variant: "outline" },
};

export function WorkflowStatusBadge({ status }: { status: WorkflowStatus }) {
  const config = workflowStatusConfig[status];
  return <Badge variant={config.variant}>{config.label}</Badge>;
}

const runStatusConfig: Record<WorkflowRunStatus, { label: string; variant: "default" | "secondary" | "outline" | "destructive" }> = {
  PENDING: { label: "等待中", variant: "outline" },
  RUNNING: { label: "运行中", variant: "default" },
  SUCCEEDED: { label: "成功", variant: "secondary" },
  FAILED: { label: "失败", variant: "destructive" },
  CANCELED: { label: "已取消", variant: "outline" },
};

export function WorkflowRunStatusBadge({ status }: { status: WorkflowRunStatus }) {
  const config = runStatusConfig[status];
  return <Badge variant={config.variant}>{config.label}</Badge>;
}

const taskStatusConfig: Record<TaskRunStatus, { label: string; variant: "default" | "secondary" | "outline" | "destructive" }> = {
  PENDING: { label: "等待中", variant: "outline" },
  RUNNING: { label: "运行中", variant: "default" },
  SUCCEEDED: { label: "成功", variant: "secondary" },
  FAILED: { label: "失败", variant: "destructive" },
  SKIPPED: { label: "已跳过", variant: "outline" },
};

export function TaskRunStatusBadge({ status }: { status: TaskRunStatus }) {
  const config = taskStatusConfig[status];
  return <Badge variant={config.variant}>{config.label}</Badge>;
}
