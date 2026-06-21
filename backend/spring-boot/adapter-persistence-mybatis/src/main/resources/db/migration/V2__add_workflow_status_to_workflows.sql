alter table workflows
    add column status text not null default 'DRAFT';

alter table workflows
    add constraint chk_workflows_status
    check (status in ('DRAFT', 'ACTIVE', 'PAUSED', 'ARCHIVED'));

alter table workflows
    alter column status drop default;
