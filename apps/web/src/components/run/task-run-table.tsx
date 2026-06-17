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
  isRunning: boolean;
}

function formatInstant(iso?: string): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("zh-CN");
}

export function TaskRunTable({ runId, isRunning }: TaskRunTableProps) {
  const { data: taskRuns, isLoading, error } = useTaskRuns(runId, isRunning);

  if (isLoading) return <div className="py-4 text-center text-muted-foreground">加载中...</div>;
  if (error) return <div className="py-4 text-center text-destructive">加载失败: {error.message}</div>;
  if (!taskRuns?.length) return <div className="py-4 text-center text-muted-foreground">暂无任务运行记录</div>;

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>位置</TableHead>
          <TableHead>任务名称</TableHead>
          <TableHead>类型</TableHead>
          <TableHead>状态</TableHead>
          <TableHead>重试</TableHead>
          <TableHead>开始</TableHead>
          <TableHead>结束</TableHead>
          <TableHead>错误</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {taskRuns.map((tr) => (
          <TableRow key={tr.id}>
            <TableCell className="text-muted-foreground">{tr.position}</TableCell>
            <TableCell className="font-medium">{tr.taskName}</TableCell>
            <TableCell className="font-mono text-xs text-muted-foreground">{tr.taskType}</TableCell>
            <TableCell><TaskRunStatusBadge status={tr.status} /></TableCell>
            <TableCell className="text-muted-foreground">
              {tr.retryCount}/{tr.maxRetries}
            </TableCell>
            <TableCell className="text-muted-foreground">{formatInstant(tr.startedAt)}</TableCell>
            <TableCell className="text-muted-foreground">{formatInstant(tr.finishedAt)}</TableCell>
            <TableCell className="max-w-xs truncate text-xs text-destructive" title={tr.errorMessage ?? ""}>
              {tr.errorMessage ?? "—"}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
