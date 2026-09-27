// Command analysis-parser reads a CS2 .dem file and writes one JSON object describing it to stdout: the map,
// per-round results, kills, grenade trajectories/detonations and periodic player position snapshots.
//
// It is a thin, custom extraction layer on top of github.com/markus-wa/demoinfocs-golang (MIT licensed) — see
// docs/cs2-analysis.md for why this library and this architecture (a small Go binary the Kotlin API shells out
// to) were chosen over the Python/Rust alternatives.
//
// Usage: analysis-parser -demo /path/to/file.dem   (prints JSON to stdout, exit 0 on success)
// On failure: a message on stderr, exit code 1.
package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"os"

	demoinfocs "github.com/markus-wa/demoinfocs-golang/v5/pkg/demoinfocs"
	common "github.com/markus-wa/demoinfocs-golang/v5/pkg/demoinfocs/common"
	events "github.com/markus-wa/demoinfocs-golang/v5/pkg/demoinfocs/events"
	"github.com/markus-wa/demoinfocs-golang/v5/pkg/demoinfocs/msg"
)

// How often (in game ticks) to record a snapshot of every player's position. At a typical 64-tick demo this is
// about one sample per second; FACEIT's 128-tick demos sample about twice as densely. Coarser than full-tick
// resolution on purpose: a whole match at every tick would be enormous, and once-a-second is already enough
// both for static heatmaps and for a watchable animated replay later.
const positionSampleIntervalTicks = 64

type roundResult struct {
	Number     int    `json:"number"`
	WinnerTeam string `json:"winnerTeam"` // "CT", "T" or "" for a tie/no winner
	CtScore    int    `json:"ctScore"`
	TScore     int    `json:"tScore"`
}

type killEvent struct {
	Round           int     `json:"round"`
	Tick            int     `json:"tick"`
	AttackerSteamID *string `json:"attackerSteamId"`
	AttackerName    *string `json:"attackerName"`
	AttackerTeam    *string `json:"attackerTeam"`
	AttackerX       *float64 `json:"attackerX"`
	AttackerY       *float64 `json:"attackerY"`
	AttackerZ       *float64 `json:"attackerZ"`
	VictimSteamID   *string `json:"victimSteamId"`
	VictimName      *string `json:"victimName"`
	VictimTeam      *string `json:"victimTeam"`
	VictimX         float64 `json:"victimX"`
	VictimY         float64 `json:"victimY"`
	VictimZ         float64 `json:"victimZ"`
	Weapon          string  `json:"weapon"`
	Headshot        bool    `json:"headshot"`
	Wallbang        bool    `json:"wallbang"`
}

type trajectoryPoint struct {
	X float64 `json:"x"`
	Y float64 `json:"y"`
	Z float64 `json:"z"`
}

type grenadeEvent struct {
	Round           int               `json:"round"`
	Type            string            `json:"type"`
	ThrowerSteamID  *string           `json:"throwerSteamId"`
	ThrowerName     *string           `json:"throwerName"`
	ThrowerTeam     *string           `json:"throwerTeam"`
	DetonateX       float64           `json:"detonateX"`
	DetonateY       float64           `json:"detonateY"`
	DetonateZ       float64           `json:"detonateZ"`
	Trajectory      []trajectoryPoint `json:"trajectory"`
}

type positionSnapshot struct {
	Round   int     `json:"round"`
	Tick    int     `json:"tick"`
	SteamID string  `json:"steamId"`
	Name    string  `json:"name"`
	Team    string  `json:"team"`
	X       float64 `json:"x"`
	Y       float64 `json:"y"`
	Z       float64 `json:"z"`
	Health  int     `json:"health"`
	Alive   bool    `json:"alive"`
}

type result struct {
	Map       string             `json:"map"`
	Rounds    []roundResult      `json:"rounds"`
	Kills     []killEvent        `json:"kills"`
	Grenades  []grenadeEvent     `json:"grenades"`
	Positions []positionSnapshot `json:"positions"`
}

func main() {
	demoPath := flag.String("demo", "", "path to the .dem file to parse")
	flag.Parse()

	if *demoPath == "" {
		fmt.Fprintln(os.Stderr, "usage: analysis-parser -demo /path/to/file.dem")
		os.Exit(1)
	}

	r, err := parse(*demoPath)
	if err != nil {
		fmt.Fprintln(os.Stderr, "parse failed:", err)
		os.Exit(1)
	}

	if err := json.NewEncoder(os.Stdout).Encode(r); err != nil {
		fmt.Fprintln(os.Stderr, "failed to write JSON output:", err)
		os.Exit(1)
	}
}

func parse(demoPath string) (*result, error) {
	f, err := os.Open(demoPath)
	if err != nil {
		return nil, fmt.Errorf("open demo: %w", err)
	}
	defer f.Close()

	p := demoinfocs.NewParser(f)
	defer p.Close()

	out := &result{}
	round := 0 // 1-based once the first round starts; see the RoundEnd handler below for the same cheap
	// round-counting approach demoinfocs-golang's own examples use (does not handle match restarts).
	grenadesInFlight := map[int64]*grenadeEvent{}

	p.RegisterNetMessageHandler(func(m *msg.CSVCMsg_ServerInfo) {
		out.Map = m.GetMapName()
	})

	p.RegisterEventHandler(func(e events.Kill) {
		round++ // rounds are 1-based; guaranteed >=1 here since kills only happen mid-round

		k := killEvent{
			Round: round,
			Tick:  p.GameState().IngameTick(),
			// %v matches how demoinfocs-golang's own print-events example formats a weapon (see e.Weapon in
			// examples/print-events); safer than assuming a `.String()` method name we haven't verified.
			Weapon:   fmt.Sprintf("%v", e.Weapon),
			Headshot: e.IsHeadshot,
			Wallbang: e.PenetratedObjects > 0,
		}
		// events.Kill exposes the attacking player as `Killer`, not `Attacker` (verified against
		// examples/print-events/print_events.go).
		if e.Killer != nil {
			pos := e.Killer.Position()
			steamID := fmt.Sprintf("%d", e.Killer.SteamID64)
			name := e.Killer.Name
			team := teamName(e.Killer.Team)
			x, y, z := pos.X, pos.Y, pos.Z
			k.AttackerSteamID, k.AttackerName, k.AttackerTeam = &steamID, &name, &team
			k.AttackerX, k.AttackerY, k.AttackerZ = &x, &y, &z
		}
		if e.Victim != nil {
			pos := e.Victim.Position()
			steamID := fmt.Sprintf("%d", e.Victim.SteamID64)
			name := e.Victim.Name
			team := teamName(e.Victim.Team)
			k.VictimSteamID, k.VictimName, k.VictimTeam = &steamID, &name, &team
			k.VictimX, k.VictimY, k.VictimZ = pos.X, pos.Y, pos.Z
		}
		out.Kills = append(out.Kills, k)
	})

	p.RegisterEventHandler(func(e events.RoundEnd) {
		if round == 0 {
			round = 1
		}
		gs := p.GameState()
		ctScore, tScore := gs.TeamCounterTerrorists().Score(), gs.TeamTerrorists().Score()
		winner := ""
		switch e.Winner { //nolint:exhaustive
		case common.TeamCounterTerrorists:
			winner = "CT"
			ctScore++ // the score hasn't been incremented for this round's winner yet at RoundEnd time
		case common.TeamTerrorists:
			winner = "T"
			tScore++
		}
		out.Rounds = append(out.Rounds, roundResult{Number: round, WinnerTeam: winner, CtScore: ctScore, TScore: tScore})
		round++
	})

	p.RegisterEventHandler(func(e events.GrenadeProjectileDestroy) {
		id := e.Projectile.UniqueID()
		g, ok := grenadesInFlight[id]
		if !ok {
			g = &grenadeEvent{Round: max(round, 1), Type: equipmentName(e.Projectile.WeaponInstance.Type)}
			if e.Projectile.Thrower != nil {
				steamID := fmt.Sprintf("%d", e.Projectile.Thrower.SteamID64)
				name := e.Projectile.Thrower.Name
				team := teamName(e.Projectile.Thrower.Team)
				g.ThrowerSteamID, g.ThrowerName, g.ThrowerTeam = &steamID, &name, &team
			}
			grenadesInFlight[id] = g
		}
		for _, point := range e.Projectile.Trajectory {
			g.Trajectory = append(g.Trajectory, trajectoryPoint{X: point.Position.X, Y: point.Position.Y, Z: point.Position.Z})
		}
		if len(g.Trajectory) > 0 {
			last := g.Trajectory[len(g.Trajectory)-1]
			g.DetonateX, g.DetonateY, g.DetonateZ = last.X, last.Y, last.Z
		}
		out.Grenades = append(out.Grenades, *g)
		delete(grenadesInFlight, id)
	})

	p.RegisterEventHandler(func(events.FrameDone) {
		tick := p.GameState().IngameTick()
		if tick%positionSampleIntervalTicks != 0 {
			return
		}
		for _, pl := range p.GameState().Participants().Playing() {
			pos := pl.Position()
			out.Positions = append(out.Positions, positionSnapshot{
				Round:   max(round, 1),
				Tick:    tick,
				SteamID: fmt.Sprintf("%d", pl.SteamID64),
				Name:    pl.Name,
				Team:    teamName(pl.Team),
				X:       pos.X,
				Y:       pos.Y,
				Z:       pos.Z,
				Health:  pl.Health(),
				Alive:   pl.IsAlive(),
			})
		}
	})

	if err := p.ParseToEnd(); err != nil {
		return nil, fmt.Errorf("parse demo: %w", err)
	}

	return out, nil
}

func teamName(t common.Team) string {
	switch t { //nolint:exhaustive
	case common.TeamCounterTerrorists:
		return "CT"
	case common.TeamTerrorists:
		return "T"
	default:
		return "SPEC"
	}
}

func equipmentName(t common.EquipmentType) string {
	switch t { //nolint:exhaustive
	case common.EqFlash:
		return "Flash"
	case common.EqSmoke:
		return "Smoke"
	case common.EqHE:
		return "HE"
	case common.EqMolotov:
		return "Molotov"
	case common.EqIncendiary:
		return "Incendiary"
	case common.EqDecoy:
		return "Decoy"
	default:
		return fmt.Sprintf("%v", t)
	}
}

func max(a, b int) int {
	if a > b {
		return a
	}
	return b
}
