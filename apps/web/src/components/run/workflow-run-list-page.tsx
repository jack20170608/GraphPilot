"use client";

import Link from "next/link";
import { useWorkflows } from "@/hooks/use-workflows";
import { useWorkflowRuns } from "@/hooks/use-workflow-runs";
import { WorkflowRunStatusBadge } from "@/components/shared/status-badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

export function WorkflowRunListPage() {
  const { data: workflows } = useWorkflows();

  if (!workflows?.length) {
    return (
      <div className="space-y-4">
        <h1 className="text-2xl font-bold">Workflow Runs</h1>
        <p className="text-muted-foreground">暂无 Workflow，请先创建 Workflow。</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Workflow Runs</h1>
      {workflows.map((wf) => (
        <WorkflowRunSection key={wf.id} workflowId={wf.id} workflowName={wf.name} />
      ))}
    </div>
  );
}

function WorkflowRunSection({
  workflowId,
  workflowName,
}: {
  workflowId: string;
  workflowName: string;
}) {
  const { data: runs, isLoading } = useWorkflowRuns(workflowId);

  return (
    <div className="space-y-2">
      <h2 className="text-lg font-semibold">
        <Link href={`/workflows/${workflowId}`} className="hover:underline">
          {workflowName}
        </Link>
      </h2>
      {isLoading ? (
        <p className="text-sm text-muted-foreground">加载中...</p>
      ) : !runs?.length ? (
        <p className="text-sm text-muted-foreground">暂无运行记录</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Run ID</TableHead>
              <TableHead>状态</TableHead>
              <TableHead>触发时间</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {runs.map((run) => (
              <TableRow key={run.id}>
                <TableCell>
                  <Link
                    href={`/workflow-runs/${run.id}`}
                    className="font-mono text-sm hover:underline"
                  >
                    {run.id.slice(0, 8)}...
                  </Link>
                </TableCell>
                <TableCell>
                  <WorkflowRunStatusBadge status={run.status} />
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {new Date(run.triggeredAt).toLocaleString("zh-CN")}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </div>
  );
}
