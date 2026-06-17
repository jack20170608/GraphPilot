"use client";

import { WorkflowTable } from "@/components/workflow/workflow-table";
import { Button } from "@/components/ui/button";
import Link from "next/link";
import { Plus } from "lucide-react";

export default function WorkflowsPage() {
  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Workflows</h1>
        <Button render={<Link href="/workflows/new" />}>
          <Plus className="mr-2 h-4 w-4" />
          创建 Workflow
        </Button>
      </div>
      <WorkflowTable />
    </div>
  );
}
