"use client";

import type { TimelineEventType, WorkflowRunTimelineEvent } from "@/lib/types";

interface WorkflowRunTimelineProps {
  events: WorkflowRunTimelineEvent[];
  isLoading?: boolean;
  error?: Error | null;
}

const EVENT_STYLES: Record<TimelineEventType, { label: string; color: string }> = {
  RUN_CREATED: { label: "运行创建", color: "bg-slate-400" },
  RUN_STARTED: { label: "运行开始", color: "bg-blue-500" },
  TASK_STARTED: { label: "任务开始", color: "bg-blue-500" },
  TASK_SUCCEEDED: { label: "任务成功", color: "bg-green-500" },
  TASK_FAILED: { label: "任务失败", color: "bg-red-500" },
  TASK_SKIPPED: { label: "任务跳过", color: "bg-zinc-400" },
  RUN_SUCCEEDED: { label: "运行成功", color: "bg-green-500" },
  RUN_FAILED: { label: "运行失败", color: "bg-red-500" },
};

export function WorkflowRunTimeline({ events, isLoading, error }: WorkflowRunTimelineProps) {
  if (isLoading) return <div className="py-4 text-center text-muted-foreground">加载中...</div>;
  if (error) return <div className="py-4 text-center text-destructive">加载失败: {error.message}</div>;
  if (!events.length) return <div className="py-4 text-center text-muted-foreground">暂无 timeline 事件</div>;

  return (
    <div className="space-y-4">
      {events.map((event) => {
        const style = EVENT_STYLES[event.type];
        return (
          <div key={event.id} className="flex gap-3">
            <div className="flex flex-col items-center">
              <span className={`mt-1 size-3 rounded-full ${style.color}`} />
              <span className="mt-1 h-full w-px bg-border" />
            </div>
            <div className="min-w-0 flex-1 rounded-lg border bg-card p-3">
              <div className="flex items-center justify-between gap-4">
                <div className="font-medium">{style.label}</div>
                <div className="text-xs text-muted-foreground">
                  {new Date(event.occurredAt).toLocaleString("zh-CN")}
                </div>
              </div>
              <div className="mt-1 text-sm text-muted-foreground">{event.message}</div>
              {event.taskId && (
                <div className="mt-2 font-mono text-xs text-muted-foreground">task: {event.taskId}</div>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
