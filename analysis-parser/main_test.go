package main

import (
	"testing"

	common "github.com/markus-wa/demoinfocs-golang/v5/pkg/demoinfocs/common"
)

func TestTeamName(t *testing.T) {
	cases := map[common.Team]string{
		common.TeamCounterTerrorists: "CT",
		common.TeamTerrorists:        "T",
		common.Team(0):               "SPEC", // zero value: anything that isn't CT/T falls through to "SPEC"
	}
	for team, want := range cases {
		if got := teamName(team); got != want {
			t.Errorf("teamName(%v) = %q, want %q", team, got, want)
		}
	}
}

func TestEquipmentName(t *testing.T) {
	cases := map[common.EquipmentType]string{
		common.EqFlash:      "Flash",
		common.EqSmoke:      "Smoke",
		common.EqHE:         "HE",
		common.EqMolotov:    "Molotov",
		common.EqIncendiary: "Incendiary",
		common.EqDecoy:      "Decoy",
	}
	for eq, want := range cases {
		if got := equipmentName(eq); got != want {
			t.Errorf("equipmentName(%v) = %q, want %q", eq, got, want)
		}
	}
}

func TestMax(t *testing.T) {
	if max(1, 2) != 2 {
		t.Error("max(1, 2) should be 2")
	}
	if max(3, 2) != 3 {
		t.Error("max(3, 2) should be 3")
	}
}
