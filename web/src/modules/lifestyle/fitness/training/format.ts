/** 62 -> "1:02", 3725 -> "1:02:05". */
export function clock(totalSeconds: number): string {
	const s = Math.max(0, Math.floor(totalSeconds));
	const h = Math.floor(s / 3600);
	const m = Math.floor((s % 3600) / 60);
	const sec = s % 60;
	const pad = (n: number) => String(n).padStart(2, '0');
	return h > 0 ? `${h}:${pad(m)}:${pad(sec)}` : `${m}:${pad(sec)}`;
}

/** 45 -> "45 min", 120 -> "2 h", 125 -> "2 h 5 min". */
export function minutesText(total: number): string {
	if (total < 60) return `${total} min`;
	return total % 60 === 0 ? `${total / 60} h` : `${Math.floor(total / 60)} h ${total % 60} min`;
}

/** 60 -> "60 kg", 62.5 -> "62.5 kg", null -> "bodyweight". */
export function kg(weight: number | null): string {
	return weight === null || weight === 0 ? 'bodyweight' : `${Number(weight.toFixed(2))} kg`;
}

/** "8 × 60 kg" */
export function setText(set: { reps: number; weightKg: number | null }): string {
	return set.weightKg ? `${set.reps} × ${Number(set.weightKg.toFixed(2))} kg` : `${set.reps} reps`;
}
