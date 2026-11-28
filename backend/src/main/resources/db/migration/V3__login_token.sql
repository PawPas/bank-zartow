-- Magic-link login tokens (epic E-ML, task T-020). The plain token never touches
-- the database: only its SHA-256 hex digest is stored (invariant I1), looked up
-- by exact match through the unique index. A token is usable iff used_at and
-- invalidated_at are both null and expires_at is in the future (invariant I2);
-- consumption is a single conditional UPDATE so races have exactly one winner
-- (invariant I3). Spent rows (used, invalidated or expired) are deleted
-- opportunistically when a new token is issued — no scheduler.
create table login_token (
    id             bigint generated always as identity primary key,
    token_hash     varchar(64) not null unique,
    created_at     timestamptz not null,
    expires_at     timestamptz not null,
    used_at        timestamptz,
    invalidated_at timestamptz
);
