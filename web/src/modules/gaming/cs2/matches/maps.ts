/**
 * Original, generated map art. Real map images belong to Valve, so none are bundled: if you put your own
 * files at web/static/maps/<slug>.png (slug = map name without the de_/cs_ prefix, e.g. mirage.png) they are used
 * automatically, and this palette is the fallback.
 */
export interface MapStyle {
	from: string;
	to: string;
}

const STYLES: Record<string, MapStyle> = {
	mirage: { from: '#e3a85f', to: '#7a4a1f' },
	inferno: { from: '#dd6a3f', to: '#5a1d12' },
	nuke: { from: '#6f8fae', to: '#1f2c3b' },
	dust2: { from: '#dcb575', to: '#7d5a2b' },
	ancient: { from: '#56945f', to: '#1c3a2a' },
	anubis: { from: '#2fb0a6', to: '#6b4f1c' },
	overpass: { from: '#78a566', to: '#2d4a3a' },
	vertigo: { from: '#6fa6df', to: '#25405f' },
	train: { from: '#8f95a1', to: '#32363e' },
	cache: { from: '#b8914f', to: '#40331f' },
	office: { from: '#a4977f', to: '#3a342b' },
	italy: { from: '#cf8f6c', to: '#5a3326' },
	agency: { from: '#7f95ac', to: '#2c3846' },
	canals: { from: '#5aa9c0', to: '#1f4050' },
	shoots: { from: '#b98d5c', to: '#3f2c1c' },
	assembly: { from: '#8b7fb3', to: '#332c4f' },
	palacio: { from: '#d49a6a', to: '#5a3320' },
	edin: { from: '#7aa08b', to: '#2b4238' }
};

/** de_mirage -> mirage */
export function mapSlug(map: string | null): string {
	return (map ?? '').replace(/^(de|cs|ar|dz)_/, '').toLowerCase();
}

/** Known maps get a hand-picked palette; anything else gets a stable colour derived from its name. */
export function mapStyle(map: string | null): MapStyle {
	const slug = mapSlug(map);
	const known = STYLES[slug];
	if (known) return known;
	if (!slug) return { from: '#64748b', to: '#1e293b' };
	let hash = 0;
	for (const ch of slug) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
	const hue = hash % 360;
	return { from: `hsl(${hue} 50% 52%)`, to: `hsl(${hue} 55% 20%)` };
}

export function mapInitial(map: string | null): string {
	return (mapSlug(map).charAt(0) || '?').toUpperCase();
}
