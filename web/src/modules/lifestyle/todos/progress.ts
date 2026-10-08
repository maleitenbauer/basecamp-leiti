import { createQuery } from '@tanstack/svelte-query';
import { today } from '$lib/dates';
import type { TodayProgress } from '$lib/dashboard/types';
import { todosApi } from './api';
import { dueGroup } from './types';

/**
 * What todos count for today: the ones that are overdue or due today and still open, plus everything finished today.
 * Todos with a later deadline or none aren't "today's" until they're done.
 */
export function useTodosProgress(enabled: () => boolean): () => TodayProgress | null {
	const now = today();

	// the same query as the Todos page and the dashboard tile
	const list = createQuery(() => ({
		queryKey: ['lifestyle', 'todos'],
		queryFn: () => todosApi.list(),
		enabled: enabled()
	}));

	return () => {
		const data = list.data;
		if (!data) return null;
		const done = data.done.filter((t) => t.doneAt && new Date(t.doneAt).toLocaleDateString('sv-SE') === now).length;
		const open = data.open.filter((t) => ['overdue', 'today'].includes(dueGroup(t.dueDate, now))).length;
		return { done, total: done + open };
	};
}
