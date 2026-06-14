-- V4: Add worker execution fields to task_runs and workflow_runs
-- Run on: 2026-06-14

-- TaskRuns: add retry, execution, and error fields
ALTER TABLE task_runs ADD COLUMN task_type text NOT NULL DEFAULT 'mock';
ALTER TABLE task_runs ADD COLUMN retry_count integer NOT NULL DEFAULT 0;
ALTER TABLE task_runs ADD COLUMN max_retries integer NOT NULL DEFAULT 3;
ALTER TABLE task_runs ADD COLUMN error_message text;
ALTER TABLE task_runs ADD COLUMN started_at timestamptz;
ALTER TABLE task_runs ADD COLUMN finished_at timestamptz;

-- TaskRuns: add input/output JSON fields (optional, for future use)
ALTER TABLE task_runs ADD COLUMN input_data jsonb;
ALTER TABLE task_runs ADD COLUMN output_data jsonb;

-- WorkflowRuns: add execution timestamps
ALTER TABLE workflow_runs ADD COLUMN started_at timestamptz;
ALTER TABLE workflow_runs ADD COLUMN finished_at timestamptz;

-- Indexes for worker queries
CREATE INDEX idx_task_runs_status ON task_runs(workflow_run_id, status);
CREATE INDEX idx_task_runs_run_retry ON task_runs(workflow_run_id, retry_count);