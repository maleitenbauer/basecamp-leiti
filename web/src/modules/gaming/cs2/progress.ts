import { createQuery } from '@tanstack/svelte-query';
import { today } from '$lib/dates';
import type { TodayProgress } from '$lib/dashboard/types';
import { cs2Api } from './api';

/** The CS2 practice routine: every active item is something to do today. */
export function useCs2Progress(enabled: () => boolean): () => TodayProgress | null {
	const date = today();

	// the same query as the improvement routine tab and the dashboard tile
	const routine = createQuery(() => ({
		queryKey: ['gaming', 'cs2', 'routine', date],
		queryFn: () => cs2Api.routine(date),
		enabled: enabled()
	}));

	return () => {
		const items = routine.data?.items.filter((i) => i.active);
		if (!items) return null;
		return { done: items.filter((i) => i.done).length, total: items.length };
	};
}
