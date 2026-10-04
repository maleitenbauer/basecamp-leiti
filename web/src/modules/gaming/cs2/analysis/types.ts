export type DemoStatus = 'UPLOADED' | 'PARSING' | 'READY' | 'FAILED';

export interface DemoSummary {
	id: number;
	originalFilename: string;
	map: string | null;
	sizeBytes: number;
	status: DemoStatus;
	errorMessage: string | null;
	uploadedAt: string;
	parsedAt: string | null;
	roundCount: number;
	killCount: number;
}

export interface DemoRound {
	number: number;
	winnerTeam: string | null;
	ctScore: number;
	tScore: number;
	freezeTimeEndTick: number | null;
}

export interface DemoKill {
	round: number;
	attackerSteamId: string | null;
	attackerName: string | null;
	attackerTeam: string | null;
	attackerX: number | null;
	attackerY: number | null;
	victimSteamId: string | null;
	victimName: string | null;
	victimTeam: string | null;
	victimX: number;
	victimY: number;
	weapon: string;
	headshot: boolean;
}

export interface DemoGrenadeTrajectoryPoint {
	x: number;
	y: number;
}

export interface DemoGrenade {
	round: number;
	type: string;
	throwerSteamId: string | null;
	throwerName: string | null;
	throwerTeam: string | null;
	throwX: number | null;
	throwY: number | null;
	detonateX: number | null;
	detonateY: number | null;
	trajectory: DemoGrenadeTrajectoryPoint[];
}

export interface DemoPosition {
	tick: number;
	steamId: string;
	name: string;
	team: string;
	x: number;
	y: number;
	health: number;
	alive: boolean;
}

export interface DemoAnalysis {
	map: string | null;
	rounds: DemoRound[];
	kills: DemoKill[];
	grenades: DemoGrenade[];
	viewerSteamId: string | null;
}

export function formatBytes(bytes: number): string {
	if (bytes < 1_000_000) return `${Math.round(bytes / 1000)} KB`;
	return `${(bytes / 1_000_000).toFixed(1)} MB`;
}
