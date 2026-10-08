-- Each user's home dashboard arrangement: which tiles are hidden and in what order. The layout is a small JSON
-- document ({"order": [...widget ids], "hidden": [...widget ids]}); the ids come from the frontend's widget registry,
-- so a module added later needs no change here.
CREATE TABLE core.dashboard_layout (
    user_id    bigint      PRIMARY KEY REFERENCES core.app_user (id) ON DELETE CASCADE,
    layout     text        NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);
