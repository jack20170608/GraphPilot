"use client";

import { use } from "react";
import { useWorkflowRun, useTaskRuns } from "@/hooks/use-workflow-runs";
import { useWorkflow } from "@/hooks/use-workflows";
import { WorkflowRunStatusBadge } from "@/components/shared/status-badge";
import { TaskRunTable } from "@/components/run/task-run-table";
import { DagViewer } from "@/components/dag/dag-viewer";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";

export default function WorkflowRunDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  return <WorkflowRunDetail runId={id} />;
}

function WorkflowRunDetail({ runId }: { runId: string }) {
  const { data: run, isLoading: runLoading, error: runError } = useWorkflowRun(runId);
  const { data: taskRuns } = useTaskRuns(runId);
  const { data: workflow } = useWorkflow(run?.workflowId ?? "");

  if (runLoading) return <div className="py-8 text-center text-muted-foreground">加载中...</div>;
  if (runError) return <div className="py-8 text-center text-destructive">加载失败: {runError.message}</div>;
  if (!run) return null;

  const taskStatuses = taskRuns
    ? Object.fromEntries(taskRuns.map((tr) => [tr.taskId, tr.status]))
    : undefined;

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2">
        <Button variant="ghost" size="icon" render={<Link href="/workflow-runs" />}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div>
          <h1 className="text-xl font-bold">Workflow Run</h1>
          <p className="text-sm text-muted-foreground font-mono">{run.id}</p>
        </div>
        <div className="ml-auto">
          <WorkflowRunStatusBadge status={run.status} />
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">运行信息</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2 text-sm">
          <div className="flex justify-between">
            <span className="text-muted-foreground">Workflow</span>
            <Link href={`/workflows/${run.workflowId}`} className="text-primary hover:underline">
              {workflow?.name ?? run.workflowId}
            </Link>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">触发时间</span>
            <span>{new Date(run.triggeredAt).toLocaleString("zh-CN")}</span>
          </div>
        </CardContent>
      </Card>

      {workflow && (
        <Tabs defaultValue="dag">
          <TabsList>
            <TabsTrigger value="dag">DAG 拓扑</TabsTrigger>
            <TabsTrigger value="tasks">任务列表</TabsTrigger>
          </TabsList>
          <TabsContent value="dag" className="mt-4">
            <DagViewer
              tasks={workflow.tasks}
              edges={workflow.edges}
              taskStatuses={taskStatuses}
            />
          </TabsContent>
          <TabsContent value="tasks" className="mt-4">
            <TaskRunTable runId={runId} />
          </TabsContent>
        </Tabs>
      )}
    </div>
  );
}
