-- Tick at which buy/freeze time ended for a round, so the replay can skip straight to when players could
-- actually move instead of replaying the frozen standoff at spawn. Null for rounds parsed before this was
-- tracked, or if the parser never saw a RoundFreezetimeEnd event for that round.
ALTER TABLE gaming.cs2_demo_round ADD COLUMN freeze_time_end_tick integer;
