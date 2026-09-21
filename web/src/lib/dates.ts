/** Local calendar date as YYYY-MM-DD (what the API expects), independent of the server's timezone. */
export const today = (): string => new Date().toLocaleDateString('sv-SE');

export function shiftDate(iso: string, days: number): string {
	const d = new Date(`${iso}T12:00:00`);
	d.setDate(d.getDate() + days);
	return d.toLocaleDateString('sv-SE');
}

/** Whole days from one YYYY-MM-DD date to another (negative when [to] is earlier). */
export function daysBetween(from: string, to: string): number {
	const a = new Date(`${from}T12:00:00`).getTime();
	const b = new Date(`${to}T12:00:00`).getTime();
	return Math.round((b - a) / 86_400_000);
}

export function weekday(iso: string): string {
	return new Date(`${iso}T12:00:00`).toLocaleDateString(undefined, { weekday: 'short' });
}

export function prettyDate(iso: string): string {
	return new Date(`${iso}T12:00:00`).toLocaleDateString(undefined, {
		weekday: 'long',
		day: 'numeric',
		month: 'long'
	});
}
