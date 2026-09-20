import { http } from '$lib/api/http';
import type { MatchesResponse, MatchSettings, UpdateMatchSettings } from './types';

const base = '/api/gaming/cs2/matches';

export const matchesApi = {
	settings: () => http<MatchSettings>(`${base}/settings`),
	saveSettings: (body: UpdateMatchSettings) =>
		http<MatchSettings>(`${base}/settings`, { method: 'PUT', body: JSON.stringify(body) }),
	faceit: (refresh = false) => http<MatchesResponse>(`${base}/faceit?refresh=${refresh}`),
	leetify: (refresh = false) => http<MatchesResponse>(`${base}/leetify?refresh=${refresh}`)
};
