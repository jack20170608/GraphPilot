create table workflow_runs (
    id text primary key check (btrim(id) <> ''),
    workflow_id text not null references workflows(id) on delete restrict,
    status text not null check (status in ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELED')),
    triggered_at timestamptz not null
);

create table task_runs (
    id text primary key check (btrim(id) <> ''),
    workflow_run_id text not null references workflow_runs(id) on delete cascade,
    task_id text not null check (btrim(task_id) <> ''),
    task_name text not null check (btrim(task_name) <> ''),
    status text not null check (status in ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED')),
    position integer not null check (position >= 0),
    created_at timestamptz not null,
    unique (workflow_run_id, task_id),
    unique (workflow_run_id, position)
);

create index idx_workflow_runs_workflow_triggered_at_id on workflow_runs(workflow_id, triggered_at, id);
create index idx_task_runs_workflow_run_position on task_runs(workflow_run_id, position);
create index idx_task_runs_workflow_run_status on task_runs(workflow_run_id, status);
