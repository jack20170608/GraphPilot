"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useCreateWorkflow } from "@/hooks/use-workflows";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { Plus, Trash2 } from "lucide-react";

interface TaskInput {
  id: string;
  name: string;
  type: string;
  configJson: string;
}

interface EdgeInput {
  fromTaskId: string;
  toTaskId: string;
}

function emptyTask(): TaskInput {
  return { id: "", name: "", type: "mock", configJson: "{}" };
}

function parseTaskConfig(value: string): Record<string, unknown> {
  const trimmed = value.trim();
  if (!trimmed) return {};
  const parsed: unknown = JSON.parse(trimmed);
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("任务 config 必须是 JSON object");
  }
  return parsed as Record<string, unknown>;
}

export function CreateWorkflowForm() {
  const router = useRouter();
  const createMutation = useCreateWorkflow();
  const [name, setName] = useState("");
  const [tasks, setTasks] = useState<TaskInput[]>([emptyTask()]);
  const [edges, setEdges] = useState<EdgeInput[]>([]);
  const [formError, setFormError] = useState<string | null>(null);

  function addTask() {
    setTasks([...tasks, emptyTask()]);
  }

  function removeTask(index: number) {
    setTasks(tasks.filter((_, i) => i !== index));
  }

  function updateTask(index: number, field: keyof TaskInput, value: string) {
    setTasks(tasks.map((task, i) => (i === index ? { ...task, [field]: value } : task)));
  }

  function addEdge() {
    setEdges([...edges, { fromTaskId: "", toTaskId: "" }]);
  }

  function removeEdge(index: number) {
    setEdges(edges.filter((_, i) => i !== index));
  }

  function updateEdge(index: number, field: keyof EdgeInput, value: string) {
    setEdges(edges.map((edge, i) => (i === index ? { ...edge, [field]: value } : edge)));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    try {
      const parsedTasks = tasks
        .filter((t) => t.id && t.name)
        .map((task) => ({
          id: task.id,
          name: task.name,
          type: task.type || "mock",
          config: parseTaskConfig(task.configJson),
        }));
      const result = await createMutation.mutateAsync({
        name,
        tasks: parsedTasks,
        edges: edges.filter((e) => e.fromTaskId && e.toTaskId),
      });
      router.push(`/workflows/${result.id}`);
    } catch (error: unknown) {
      setFormError(error instanceof Error ? error.message : "创建 Workflow 失败");
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-6 max-w-3xl">
      <div className="space-y-2">
        <Label htmlFor="name">Workflow 名称</Label>
        <Input
          id="name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="输入 Workflow 名称"
          required
        />
      </div>

      <Separator />

      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-base">任务定义</CardTitle>
          <Button type="button" variant="outline" size="sm" onClick={addTask}>
            <Plus className="mr-1 h-3 w-3" /> 添加任务
          </Button>
        </CardHeader>
        <CardContent className="space-y-4">
          {tasks.map((task, i) => (
            <div key={i} className="space-y-3 rounded-lg border p-3">
              <div className="grid gap-2 md:grid-cols-[1fr_1fr_120px_auto] md:items-end">
                <div>
                  <Label className="text-xs text-muted-foreground">任务 ID</Label>
                  <Input
                    value={task.id}
                    onChange={(e) => updateTask(i, "id", e.target.value)}
                    placeholder="task-id"
                    required
                  />
                </div>
                <div>
                  <Label className="text-xs text-muted-foreground">任务名称</Label>
                  <Input
                    value={task.name}
                    onChange={(e) => updateTask(i, "name", e.target.value)}
                    placeholder="任务名称"
                    required
                  />
                </div>
                <div>
                  <Label className="text-xs text-muted-foreground">类型</Label>
                  <Input
                    value={task.type}
                    onChange={(e) => updateTask(i, "type", e.target.value)}
                    placeholder="mock"
                  />
                </div>
                {tasks.length > 1 && (
                  <Button type="button" variant="ghost" size="icon" onClick={() => removeTask(i)}>
                    <Trash2 className="h-4 w-4" />
                  </Button>
                )}
              </div>
              <div>
                <Label className="text-xs text-muted-foreground">静态 Config JSON</Label>
                <textarea
                  value={task.configJson}
                  onChange={(e) => updateTask(i, "configJson", e.target.value)}
                  className="mt-1 min-h-20 w-full rounded-md border border-input bg-background px-3 py-2 font-mono text-xs shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50"
                  placeholder='{"success": true, "delayMs": 0}'
                />
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-base">依赖边</CardTitle>
          <Button type="button" variant="outline" size="sm" onClick={addEdge}>
            <Plus className="mr-1 h-3 w-3" /> 添加边
          </Button>
        </CardHeader>
        <CardContent className="space-y-3">
          {edges.length === 0 && (
            <p className="text-sm text-muted-foreground py-2">
              暂无依赖边。添加边来定义任务之间的执行顺序。
            </p>
          )}
          {edges.map((edge, i) => (
            <div key={i} className="flex items-end gap-2">
              <div className="flex-1">
                <Label className="text-xs text-muted-foreground">上游任务 ID</Label>
                <Input
                  value={edge.fromTaskId}
                  onChange={(e) => updateEdge(i, "fromTaskId", e.target.value)}
                  placeholder="from-task-id"
                  required
                />
              </div>
              <div className="flex-1">
                <Label className="text-xs text-muted-foreground">下游任务 ID</Label>
                <Input
                  value={edge.toTaskId}
                  onChange={(e) => updateEdge(i, "toTaskId", e.target.value)}
                  placeholder="to-task-id"
                  required
                />
              </div>
              <Button type="button" variant="ghost" size="icon" onClick={() => removeEdge(i)}>
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          ))}
        </CardContent>
      </Card>

      {formError && <div className="rounded-md border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">{formError}</div>}

      <div className="flex gap-2">
        <Button type="submit" disabled={createMutation.isPending}>
          {createMutation.isPending ? "创建中..." : "创建 Workflow"}
        </Button>
        <Button type="button" variant="outline" onClick={() => router.back()}>
          取消
        </Button>
      </div>
    </form>
  );
}
