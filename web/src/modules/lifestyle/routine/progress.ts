import { createQuery } from '@tanstack/svelte-query';
import { today } from '$lib/dates';
import type { TodayProgress } from '$lib/dashboard/types';
import { routineApi } from './api';

/** What routines have to be done today: the ones still open plus the ones already done today. */
export function useRoutineProgress(enabled: () => boolean): () => TodayProgress | null {
	const date = today();

	// the same query as the Routine page and the dashboard tile
	const overview = createQuery(() => ({
		queryKey: ['lifestyle', 'routine', 'overview', date],
		queryFn: () => routineApi.overview(date),
		enabled: enabled()
	}));

	return () => {
		const routines = overview.data?.routines;
		if (!routines) return null;
		const done = routines.filter((r) => r.doneToday).length;
		const open = routines.filter((r) => r.state === 'OPEN').length;
		return { done, total: done + open };
	};
}
