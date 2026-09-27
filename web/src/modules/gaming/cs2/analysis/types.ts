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

export interface DemoGrenade {
	round: number;
	type: string;
	throwerName: string | null;
	throwerTeam: string | null;
	detonateX: number | null;
	detonateY: number | null;
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
