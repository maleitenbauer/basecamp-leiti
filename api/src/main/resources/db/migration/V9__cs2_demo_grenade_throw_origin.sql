-- Where the thrower was standing at the moment of the throw, not just where the grenade detonated. Derived
-- from the grenade's own trajectory (its first recorded point), not a separately tracked event — see
-- analysis-parser/main.go's GrenadeProjectileDestroy handler.
ALTER TABLE gaming.cs2_demo_grenade ADD COLUMN throw_x double precision;
ALTER TABLE gaming.cs2_demo_grenade ADD COLUMN throw_y double precision;
ALTER TABLE gaming.cs2_demo_grenade ADD COLUMN throw_z double precision;
