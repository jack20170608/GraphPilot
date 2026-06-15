"use client";

import { CreateWorkflowForm } from "@/components/workflow/create-workflow-form";

export default function NewWorkflowPage() {
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">创建 Workflow</h1>
      <CreateWorkflowForm />
    </div>
  );
}
