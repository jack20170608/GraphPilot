-- V6: Create workflow run timeline events
-- Append-only structured events used by run detail API and UI timeline.

create table workflow_run_timeline_events (
    id text primary key,
    workflow_run_id text not null references workflow_runs(id) on delete cascade,
    task_run_id text,
    task_id text,
    type text not null,
    message text not null check (btrim(message) <> ''),
    occurred_at timestamptz not null
);

create index idx_workflow_run_timeline_events_run_time
    on workflow_run_timeline_events(workflow_run_id, occurred_at, id);
