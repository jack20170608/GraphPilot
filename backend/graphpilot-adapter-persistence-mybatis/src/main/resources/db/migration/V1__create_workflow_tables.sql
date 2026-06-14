create table workflows (
    id text primary key check (btrim(id) <> ''),
    name text not null check (btrim(name) <> ''),
    status text not null check (status in ('DRAFT', 'ACTIVE', 'PAUSED', 'ARCHIVED')),
    created_at timestamptz not null
);

create table workflow_tasks (
    workflow_id text not null references workflows(id) on delete cascade,
    task_id text not null check (btrim(task_id) <> ''),
    name text not null check (btrim(name) <> ''),
    position integer not null check (position >= 0),
    primary key (workflow_id, task_id),
    unique (workflow_id, position)
);

create table workflow_edges (
    workflow_id text not null references workflows(id) on delete cascade,
    source_task_id text not null,
    target_task_id text not null,
    position integer not null check (position >= 0),
    primary key (workflow_id, source_task_id, target_task_id),
    unique (workflow_id, position),
    check (btrim(source_task_id) <> ''),
    check (btrim(target_task_id) <> ''),
    check (source_task_id <> target_task_id),
    foreign key (workflow_id, source_task_id) references workflow_tasks(workflow_id, task_id) on delete cascade,
    foreign key (workflow_id, target_task_id) references workflow_tasks(workflow_id, task_id) on delete cascade
);

create index idx_workflows_created_at_id on workflows(created_at, id);
create index idx_workflow_tasks_workflow_position on workflow_tasks(workflow_id, position);
create index idx_workflow_edges_workflow_position on workflow_edges(workflow_id, position);
create index idx_workflow_edges_workflow_target_task on workflow_edges(workflow_id, target_task_id);
