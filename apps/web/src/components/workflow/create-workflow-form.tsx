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
}

interface EdgeInput {
  fromTaskId: string;
  toTaskId: string;
}

export function CreateWorkflowForm() {
  const router = useRouter();
  const createMutation = useCreateWorkflow();
  const [name, setName] = useState("");
  const [tasks, setTasks] = useState<TaskInput[]>([{ id: "", name: "" }]);
  const [edges, setEdges] = useState<EdgeInput[]>([]);

  function addTask() {
    setTasks([...tasks, { id: "", name: "" }]);
  }

  function removeTask(index: number) {
    setTasks(tasks.filter((_, i) => i !== index));
  }

  function updateTask(index: number, field: keyof TaskInput, value: string) {
    const updated = [...tasks];
    updated[index] = { ...updated[index], [field]: value };
    setTasks(updated);
  }

  function addEdge() {
    setEdges([...edges, { fromTaskId: "", toTaskId: "" }]);
  }

  function removeEdge(index: number) {
    setEdges(edges.filter((_, i) => i !== index));
  }

  function updateEdge(index: number, field: keyof EdgeInput, value: string) {
    const updated = [...edges];
    updated[index] = { ...updated[index], [field]: value };
    setEdges(updated);
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const result = await createMutation.mutateAsync({
      name,
      tasks: tasks.filter((t) => t.id && t.name),
      edges: edges.filter((e) => e.fromTaskId && e.toTaskId),
    });
    router.push(`/workflows/${result.id}`);
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-6 max-w-2xl">
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
        <CardContent className="space-y-3">
          {tasks.map((task, i) => (
            <div key={i} className="flex items-end gap-2">
              <div className="flex-1">
                <Label className="text-xs text-muted-foreground">任务 ID</Label>
                <Input
                  value={task.id}
                  onChange={(e) => updateTask(i, "id", e.target.value)}
                  placeholder="task-id"
                  required
                />
              </div>
              <div className="flex-1">
                <Label className="text-xs text-muted-foreground">任务名称</Label>
                <Input
                  value={task.name}
                  onChange={(e) => updateTask(i, "name", e.target.value)}
                  placeholder="任务名称"
                  required
                />
              </div>
              {tasks.length > 1 && (
                <Button type="button" variant="ghost" size="icon" onClick={() => removeTask(i)}>
                  <Trash2 className="h-4 w-4" />
                </Button>
              )}
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
