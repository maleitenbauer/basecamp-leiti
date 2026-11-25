import { http } from '$lib/api/http';
import type { QueryClient } from '@tanstack/svelte-query';
import type { FinishResult, PlanInput, SetValues, TrainingOverview, Workout } from './types';

const base = '/api/logbook/fitness/training';

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });

export const trainingKey = ['lifestyle', 'fitness', 'training'] as const;
export const overviewKey = (date: string) => [...trainingKey, 'overview', date] as const;

export const trainingApi = {
	overview: (date: string) => http<TrainingOverview>(`${base}?date=${date}`),

	createPlan: (body: PlanInput) => http<{ id: number }>(`${base}/plans`, json('POST', body)),
	updatePlan: (id: number, body: PlanInput) => http<void>(`${base}/plans/${id}`, json('PUT', body)),
	deletePlan: (id: number) => http<void>(`${base}/plans/${id}`, { method: 'DELETE' }),

	/** [planId] null starts an empty workout; [day] is the user's calendar day. Every call below returns the whole workout. */
	start: (planId: number | null, day: string) => http<Workout>(`${base}/workouts`, json('POST', { planId, day })),
	addExercise: (id: number, name: string) => http<Workout>(`${base}/workouts/${id}/exercises`, json('POST', { name })),
	logSet: (id: number, exerciseId: number, set: SetValues) =>
		http<Workout>(`${base}/workouts/${id}/exercises/${exerciseId}/sets`, json('POST', set)),
	updateSet: (id: number, setId: number, set: SetValues) =>
		http<Workout>(`${base}/workouts/${id}/sets/${setId}`, json('PATCH', set)),
	deleteSet: (id: number, setId: number) => http<Workout>(`${base}/workouts/${id}/sets/${setId}`, { method: 'DELETE' }),
	finish: (id: number) => http<FinishResult>(`${base}/workouts/${id}/finish`, { method: 'POST' }),
	discard: (id: number) => http<void>(`${base}/workouts/${id}`, { method: 'DELETE' })
};

/** Shows the workout the server just returned right away, in every cached overview. */
export function showWorkout(queryClient: QueryClient, workout: Workout | null) {
	queryClient.setQueriesData<TrainingOverview>({ queryKey: [...trainingKey, 'overview'] }, (old) =>
		old ? { ...old, active: workout } : old
	);
}
