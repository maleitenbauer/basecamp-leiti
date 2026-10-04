import { mapSlug } from '../matches/maps';

/**
 * Per-map world-to-radar-image calibration: pos_x/pos_y are the world coordinate of the image's top-left corner,
 * scale is world units per pixel. These are Valve's own published overview constants (baked into every CS
 * install as overviews/<map>.txt) — plain numeric facts, not the radar artwork itself, so unlike the images below
 * they're fine to keep in source. Sourced from demoinfocs-golang's bundled copies (MIT-licensed repo,
 * examples/_assets/metadata/*.txt), cross-checked against the same numbers published by other open-source CS
 * radar tools. Assumes the standard 1024x1024 overview image resolution.
 */
interface RadarCalibration {
	posX: number;
	posY: number;
	scale: number;
}

const RADAR_IMAGE_SIZE = 1024;

const CALIBRATION: Record<string, RadarCalibration> = {
	dust2: { posX: -2476, posY: 3239, scale: 4.4 },
	mirage: { posX: -3230, posY: 1713, scale: 5.0 },
	inferno: { posX: -2087, posY: 3870, scale: 4.9 },
	nuke: { posX: -3453, posY: 2887, scale: 7 },
	overpass: { posX: -4831, posY: 1781, scale: 5.2 },
	vertigo: { posX: -3168, posY: 1762, scale: 4.0 },
	ancient: { posX: -2953, posY: 2164, scale: 5 },
	anubis: { posX: -2796, posY: 3328, scale: 5.22 },
	train: { posX: -2308, posY: 2078, scale: 4.082077 }
};

/**
 * World (x, y) -> percentage position on a same-aspect-ratio radar image (0-100 for both axes), or null if this
 * map has no calibration data. Put your own radar image at web/static/maps/radar/<slug>.png (slug = mapSlug(map),
 * e.g. mirage.png) to see it as the background — the images aren't bundled for the same reason as
 * web/static/maps/*.png (Valve's art, not ours to redistribute).
 */
export function worldToRadarPercent(map: string | null, x: number, y: number): { xPct: number; yPct: number } | null {
	const cal = CALIBRATION[mapSlug(map)];
	if (!cal) return null;
	const px = (x - cal.posX) / cal.scale;
	const py = (cal.posY - y) / cal.scale;
	return { xPct: (px / RADAR_IMAGE_SIZE) * 100, yPct: (py / RADAR_IMAGE_SIZE) * 100 };
}

export function hasRadarCalibration(map: string | null): boolean {
	return mapSlug(map) in CALIBRATION;
}

/**
 * Fallback for uncalibrated maps: scale a set of world (x, y) points to fill the 0-100 plot area, so a demo or
 * round still shows relative positions/movement even without real map coordinates.
 */
export function makeBboxTransform(
	points: { x: number; y: number }[]
): (x: number, y: number) => [number, number] {
	const xs = points.map((p) => p.x);
	const ys = points.map((p) => p.y);
	const minX = xs.length ? Math.min(...xs) : 0;
	const maxX = xs.length ? Math.max(...xs) : 1;
	const minY = ys.length ? Math.min(...ys) : 0;
	const maxY = ys.length ? Math.max(...ys) : 1;
	const spanX = maxX - minX || 1;
	const spanY = maxY - minY || 1;
	// SVG y grows downward; game Y typically grows "north", so flip it for a more intuitive top-down feel.
	return (x, y) => [((x - minX) / spanX) * 100, 100 - ((y - minY) / spanY) * 100];
}
