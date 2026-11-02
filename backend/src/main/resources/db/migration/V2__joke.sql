-- First domain table of epic 1: the joke bank core (task T-013).
-- Only the complete original joke, UTC timestamps and an optimistic-lock version
-- live here; status, tags and alternate punchlines arrive in later tasks
-- (T-015, T-016, T-018) as separate migrations.
create table joke (
    id         bigint generated always as identity primary key,
    content    text not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version    bigint not null
);

-- The list is ordered newest first; id breaks ties so rows created within the
-- same instant keep a stable, deterministic order.
create index idx_joke_created_at on joke (created_at desc, id desc);
