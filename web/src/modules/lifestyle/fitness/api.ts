import { http } from '$lib/api/http';
import type {
	AddMealEntry,
	CreateFood,
	Food,
	MealEntry,
	NutritionDay,
	NutritionGoal,
	OnlineFood,
	UpdateMealEntry
} from './types';

const base = '/api/logbook/fitness/nutrition';

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });

export const nutritionApi = {
	goal: () => http<NutritionGoal>(`${base}/goal`),
	setGoal: (dailyKcal: number) => http<NutritionGoal>(`${base}/goal`, json('PUT', { dailyKcal })),

	day: (date: string) => http<NutritionDay>(`${base}/day?date=${date}`),

	addEntry: (body: AddMealEntry) => http<MealEntry>(`${base}/entries`, json('POST', body)),
	updateEntry: (id: number, body: UpdateMealEntry) => http<MealEntry>(`${base}/entries/${id}`, json('PATCH', body)),
	deleteEntry: (id: number) => http<void>(`${base}/entries/${id}`, { method: 'DELETE' }),

	foods: () => http<Food[]>(`${base}/foods`),
	createFood: (body: CreateFood) => http<Food>(`${base}/foods`, json('POST', body)),
	deleteFood: (id: number) => http<void>(`${base}/foods/${id}`, { method: 'DELETE' }),
	searchOnline: (q: string) => http<OnlineFood[]>(`${base}/foods/online?q=${encodeURIComponent(q)}`)
};
