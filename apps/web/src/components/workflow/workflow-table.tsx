"use client";

import Link from "next/link";
import { useWorkflows } from "@/hooks/use-workflows";
import { WorkflowStatusBadge } from "@/components/shared/status-badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Plus } from "lucide-react";

export function WorkflowTable() {
  const { data: workflows, isLoading, error } = useWorkflows();

  if (isLoading) return <div className="py-8 text-center text-muted-foreground">加载中...</div>;
  if (error) return <div className="py-8 text-center text-destructive">加载失败: {error.message}</div>;
  if (!workflows?.length) {
    return (
      <div className="flex flex-col items-center justify-center py-16 gap-4">
        <p className="text-muted-foreground">暂无 Workflow</p>
        <Button asChild>
          <Link href="/workflows/new"><Plus className="mr-2 h-4 w-4" />创建 Workflow</Link>
        </Button>
      </div>
    );
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>名称</TableHead>
          <TableHead>状态</TableHead>
          <TableHead>任务数</TableHead>
          <TableHead>创建时间</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {workflows.map((wf) => (
          <TableRow key={wf.id}>
            <TableCell>
              <Link href={`/workflows/${wf.id}`} className="font-medium hover:underline">
                {wf.name}
              </Link>
            </TableCell>
            <TableCell><WorkflowStatusBadge status={wf.status} /></TableCell>
            <TableCell>{wf.tasks.length}</TableCell>
            <TableCell className="text-muted-foreground">
              {new Date(wf.createdAt).toLocaleString("zh-CN")}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
