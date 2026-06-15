"use client";

import { WorkflowDetail } from "@/components/workflow/workflow-detail";

export default async function WorkflowDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <WorkflowDetail workflowId={id} />;
}
