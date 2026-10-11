import { shiftDate } from '$lib/dates';

/** The Monday on or before a YYYY-MM-DD date. Weeks run Monday to Sunday, the same as on the server. */
export function mondayOf(iso: string): string {
	const weekday = new Date(`${iso}T12:00:00`).getDay(); // 0 = Sunday
	return shiftDate(iso, -((weekday + 6) % 7));
}

/** "2 Mar – 8 Mar 2026", with the year only once (and both when the week crosses New Year). */
export function formatWeek(start: string): string {
	const end = shiftDate(start, 6);
	const from = new Date(`${start}T12:00:00`);
	const to = new Date(`${end}T12:00:00`);
	const short = (d: Date) => d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
	const sameYear = from.getFullYear() === to.getFullYear();
	const first = sameYear ? short(from) : `${short(from)} ${from.getFullYear()}`;
	return `${first} – ${short(to)} ${to.getFullYear()}`;
}
