"use client";

import Link from "next/link";
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

interface WorkflowRunTableProps {
  workflowId: string;
}

export function WorkflowRunTable({ workflowId }: WorkflowRunTableProps) {
  const { data: runs, isLoading, error } = useWorkflowRuns(workflowId);

  if (isLoading) return <div className="py-8 text-center text-muted-foreground">加载中...</div>;
  if (error) return <div className="py-8 text-center text-destructive">加载失败: {error.message}</div>;
  if (!runs?.length) return <div className="py-8 text-center text-muted-foreground">暂无运行记录</div>;

  return (
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
              <Link href={`/workflow-runs/${run.id}`} className="font-mono text-sm hover:underline">
                {run.id.slice(0, 8)}...
              </Link>
            </TableCell>
            <TableCell><WorkflowRunStatusBadge status={run.status} /></TableCell>
            <TableCell className="text-muted-foreground">
              {new Date(run.triggeredAt).toLocaleString("zh-CN")}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
