-- Gaming > Counter-Strike 2 > Matches.
-- Only the user's own link settings are stored. Match data itself is fetched live from FACEIT / Leetify and
-- deliberately NOT persisted (Leetify asks developers not to store their API data).
CREATE TABLE gaming.cs2_match_profile (
    user_id          bigint PRIMARY KEY REFERENCES core.app_user (id) ON DELETE CASCADE,
    faceit_nickname  varchar(64),
    faceit_player_id varchar(64),
    steam64_id       varchar(20),
    updated_at       timestamptz NOT NULL DEFAULT now()
);
