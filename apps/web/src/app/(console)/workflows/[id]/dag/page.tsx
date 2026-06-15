"use client";

import { useWorkflow } from "@/hooks/use-workflows";
import { useTaskRuns } from "@/hooks/use-workflow-runs";
import { DagViewer } from "@/components/dag/dag-viewer";
import { Button } from "@/components/ui/button";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";

export default async function DagViewerPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <DagViewerWrapper workflowId={id} />;
}

function DagViewerWrapper({ workflowId }: { workflowId: string }) {
  const { data: workflow, isLoading, error } = useWorkflow(workflowId);

  if (isLoading) return <div className="py-8 text-center text-muted-foreground">加载中...</div>;
  if (error) return <div className="py-8 text-center text-destructive">加载失败: {error.message}</div>;
  if (!workflow) return null;

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <Button variant="ghost" size="icon" asChild>
          <Link href={`/workflows/${workflowId}`}>
            <ArrowLeft className="h-4 w-4" />
          </Link>
        </Button>
        <h1 className="text-xl font-bold">DAG: {workflow.name}</h1>
      </div>
      <DagViewer tasks={workflow.tasks} edges={workflow.edges} />
    </div>
  );
}
