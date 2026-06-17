"use client";

import { useWorkflow } from "@/hooks/use-workflows";
import { WorkflowStatusBadge } from "@/components/shared/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useActivateWorkflow, usePauseWorkflow, useResumeWorkflow, useArchiveWorkflow } from "@/hooks/use-workflows";
import { useTriggerWorkflowRun as useTriggerRun } from "@/hooks/use-workflow-runs";
import { MoreHorizontal, Play, Pause, PlayCircle, Archive, Rocket } from "lucide-react";
import Link from "next/link";

interface WorkflowDetailProps {
  workflowId: string;
}

export function WorkflowDetail({ workflowId }: WorkflowDetailProps) {
  const { data: workflow, isLoading, error } = useWorkflow(workflowId);
  const activateMutation = useActivateWorkflow();
  const pauseMutation = usePauseWorkflow();
  const resumeMutation = useResumeWorkflow();
  const archiveMutation = useArchiveWorkflow();
  const triggerRunMutation = useTriggerRun();

  if (isLoading) return <div className="py-8 text-center text-muted-foreground">加载中...</div>;
  if (error) return <div className="py-8 text-center text-destructive">加载失败: {error.message}</div>;
  if (!workflow) return null;

  const canActivate = workflow.status === "DRAFT" || workflow.status === "PAUSED";
  const canPause = workflow.status === "ACTIVE";
  const canResume = workflow.status === "PAUSED";
  const canArchive = workflow.status !== "ARCHIVED";
  const canTrigger = workflow.status === "ACTIVE";

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">{workflow.name}</h1>
          <p className="text-sm text-muted-foreground mt-1">ID: {workflow.id}</p>
        </div>
        <div className="flex items-center gap-2">
          <WorkflowStatusBadge status={workflow.status} />
          {canTrigger && (
            <Button
              size="sm"
              onClick={() => triggerRunMutation.mutate(workflow.id)}
              disabled={triggerRunMutation.isPending}
            >
              <Rocket className="mr-2 h-4 w-4" />
              触发运行
            </Button>
          )}
          <DropdownMenu>
            <DropdownMenuTrigger render={<Button variant="outline" size="icon" />}>
              <MoreHorizontal className="h-4 w-4" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {canActivate && (
                <DropdownMenuItem onClick={() => activateMutation.mutate(workflow.id)}>
                  <Play className="mr-2 h-4 w-4" /> 激活
                </DropdownMenuItem>
              )}
              {canPause && (
                <DropdownMenuItem onClick={() => pauseMutation.mutate(workflow.id)}>
                  <Pause className="mr-2 h-4 w-4" /> 暂停
                </DropdownMenuItem>
              )}
              {canResume && (
                <DropdownMenuItem onClick={() => resumeMutation.mutate(workflow.id)}>
                  <PlayCircle className="mr-2 h-4 w-4" /> 恢复
                </DropdownMenuItem>
              )}
              {canArchive && (
                <DropdownMenuItem onClick={() => archiveMutation.mutate(workflow.id)}>
                  <Archive className="mr-2 h-4 w-4" /> 归档
                </DropdownMenuItem>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      <Separator />

      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">基本信息</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-muted-foreground">创建时间</span>
              <span>{new Date(workflow.createdAt).toLocaleString("zh-CN")}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">任务数</span>
              <span>{workflow.tasks.length}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">依赖边数</span>
              <span>{workflow.edges.length}</span>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">任务列表</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-1">
              {workflow.tasks.map((task) => (
                <div key={task.id} className="flex items-center gap-2 text-sm">
                  <span className="font-mono text-xs bg-muted px-1.5 py-0.5 rounded">{task.id}</span>
                  <span>{task.name}</span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">DAG 拓扑</CardTitle>
        </CardHeader>
        <CardContent>
          <Link href={`/workflows/${workflow.id}/dag`} className="text-sm text-primary hover:underline">
            打开 DAG 查看器 →
          </Link>
        </CardContent>
      </Card>
    </div>
  );
}
