-- V5: Add static task type/config to workflow task definitions
-- Used by worker handlers as immutable JSON input.

ALTER TABLE workflow_tasks ADD COLUMN type text NOT NULL DEFAULT 'mock';
ALTER TABLE workflow_tasks ADD COLUMN config jsonb NOT NULL DEFAULT '{}';
