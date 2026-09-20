export type MatchResult = 'WIN' | 'LOSS' | 'DRAW';

export interface MatchSummary {
	id: string;
	source: 'faceit' | 'matchmaking' | 'matchmaking_competitive';
	mode: string | null;
	finishedAt: string | null;
	map: string | null;
	result: MatchResult | null;
	score: string | null;
	kills: number | null;
	deaths: number | null;
	assists: number | null;
	kdRatio: number | null;
	adr: number | null;
	headshotPercent: number | null;
	headshotKills: number | null;
	rating: number | null;
	ratingLabel: string | null;
	url: string | null;
	details: Record<string, string> | null;
}

export interface PlayerInfo {
	nickname: string;
	level: number | null;
	elo: number | null;
}

export interface MatchesResponse {
	configured: boolean;
	message: string | null;
	matches: MatchSummary[];
	player: PlayerInfo | null;
	fetchedAt: string;
	sourceUrl: string | null;
}

export interface MatchSettings {
	faceitNickname: string | null;
	steam64Id: string | null;
	faceitAvailable: boolean;
}

export interface UpdateMatchSettings {
	faceitNickname: string | null;
	steam64Id: string | null;
}

/**
 * The API reports the Leetify rating as a small decimal (0.0885); the Leetify app shows it multiplied by 100
 * with a sign (+8.85). Display it the way the app does.
 */
export function formatLeetifyRating(rating: number): string {
	const value = rating * 100;
	return `${value >= 0 ? '+' : ''}${value.toFixed(2)}`;
}

export const ratingTone = (rating: number): string => (rating >= 0 ? 'text-emerald-400' : 'text-red-400');

/** de_mirage -> Mirage */
export function prettyMap(map: string | null): string {
	if (!map) return 'Unknown map';
	const name = map.replace(/^(de|cs|ar|dz)_/, '');
	return name.charAt(0).toUpperCase() + name.slice(1);
}
