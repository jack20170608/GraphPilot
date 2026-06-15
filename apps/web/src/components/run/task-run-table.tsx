"use client";

import { useTaskRuns } from "@/hooks/use-workflow-runs";
import { TaskRunStatusBadge } from "@/components/shared/status-badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

interface TaskRunTableProps {
  runId: string;
}

export function TaskRunTable({ runId }: TaskRunTableProps) {
  const { data: taskRuns, isLoading, error } = useTaskRuns(runId);

  if (isLoading) return <div className="py-4 text-center text-muted-foreground">加载中...</div>;
  if (error) return <div className="py-4 text-center text-destructive">加载失败: {error.message}</div>;
  if (!taskRuns?.length) return <div className="py-4 text-center text-muted-foreground">暂无任务运行记录</div>;

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>位置</TableHead>
          <TableHead>任务名称</TableHead>
          <TableHead>任务 ID</TableHead>
          <TableHead>状态</TableHead>
          <TableHead>创建时间</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {taskRuns.map((tr) => (
          <TableRow key={tr.id}>
            <TableCell className="text-muted-foreground">{tr.position}</TableCell>
            <TableCell className="font-medium">{tr.taskName}</TableCell>
            <TableCell className="font-mono text-xs">{tr.taskId}</TableCell>
            <TableCell><TaskRunStatusBadge status={tr.status} /></TableCell>
            <TableCell className="text-muted-foreground">
              {new Date(tr.createdAt).toLocaleString("zh-CN")}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
