-- Lists are ordered newest first with the identifier as a tie-breaker so that pagination is
-- stable when timestamps collide. These indexes match those orderings exactly.

-- The unfiltered incident list (the console's default view) previously had to sort the table.
CREATE INDEX ix_incidents_opened ON incidents (opened_at DESC, id DESC);

-- Deployment history across all environments of one workload; the existing index leads with
-- the environment and so only serves the per-environment view.
CREATE INDEX ix_deployments_workload_time ON deployments (workload_id, deployed_at DESC, id DESC);
