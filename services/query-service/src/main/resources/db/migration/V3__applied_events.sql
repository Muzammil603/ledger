CREATE TABLE IF NOT EXISTS applied_events (
  aggregate_id TEXT NOT NULL,
  version BIGINT NOT NULL,
  PRIMARY KEY (aggregate_id, version)
);
