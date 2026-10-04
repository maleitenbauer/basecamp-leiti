export type FoodSource = 'MANUAL' | 'OPEN_FOOD_FACTS';

export interface NutritionGoal {
	dailyKcal: number | null;
}

export interface MealEntry {
	id: number;
	foodId: number | null;
	name: string;
	grams: number | null;
	kcal: number;
	eatenAt: string;
}

export interface NutritionDay {
	date: string;
	goalKcal: number | null;
	consumedKcal: number;
	/** Negative once the goal is exceeded; null when no goal is set. */
	remainingKcal: number | null;
	entries: MealEntry[];
}

export interface Food {
	id: number;
	name: string;
	brand: string | null;
	kcalPer100g: number;
	portionGrams: number | null;
	portionLabel: string | null;
	barcode: string | null;
	source: FoodSource;
	lastGrams: number | null;
	useCount: number;
	lastUsedAt: string | null;
}

/** A product found on Open Food Facts; not saved until the user picks it. */
export interface OnlineFood {
	code: string | null;
	name: string;
	brand: string | null;
	kcalPer100g: number;
}

export interface CreateFood {
	name: string;
	brand?: string;
	kcalPer100g: number;
	portionGrams?: number;
	portionLabel?: string;
	barcode?: string;
	source?: FoodSource;
}

export interface AddMealEntry {
	day: string;
	eatenAt?: string;
	foodId?: number;
	grams?: number;
	name?: string;
	kcal?: number;
}

export interface UpdateMealEntry {
	grams?: number;
	kcal?: number;
	eatenAt?: string;
	day?: string;
}

export function kcalFor(kcalPer100g: number, grams: number): number {
	return Math.round((kcalPer100g * grams) / 100);
}

/** YYYY-MM-DD in the browser's own time zone — what "today" means to the person looking at the screen. */
export function localDateString(d: Date = new Date()): string {
	const month = String(d.getMonth() + 1).padStart(2, '0');
	const day = String(d.getDate()).padStart(2, '0');
	return `${d.getFullYear()}-${month}-${day}`;
}

export function shiftDate(date: string, days: number): string {
	const [y, m, d] = date.split('-').map(Number);
	return localDateString(new Date(y, m - 1, d + days));
}

export function localTimeString(d: Date = new Date()): string {
	return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

/** Combines a YYYY-MM-DD day and an HH:MM time, both in the browser's time zone, into an ISO instant. */
export function toInstant(date: string, time: string): string {
	const [y, m, d] = date.split('-').map(Number);
	const [hh, mm] = time.split(':').map(Number);
	return new Date(y, m - 1, d, hh, mm).toISOString();
}
