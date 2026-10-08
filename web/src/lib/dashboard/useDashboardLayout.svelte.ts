import { createMutation, createQuery, useQueryClient } from '@tanstack/svelte-query';
import { dashboardApi } from './api';
import { applyVisibleOrder, emptyLayout, moved, orderedWidgets, parseLayout, withHidden, type Layout } from './layout';
import { widgets } from './registry';

const KEY = ['dashboard', 'layout'];
// Only a cache so the dashboard can paint in the right order before the server answers; the server's copy wins.
const CACHE_KEY = 'basecamp.dashboard.layout.cache';

function readCache(): Layout | undefined {
	try {
		const raw = localStorage.getItem(CACHE_KEY);
		return raw ? parseLayout(raw) : undefined;
	} catch {
		return undefined;
	}
}

function writeCache(layout: Layout) {
	try {
		localStorage.setItem(CACHE_KEY, JSON.stringify(layout));
	} catch {
		// storage blocked or full: only the instant first paint is lost
	}
}

/**
 * The signed-in user's dashboard arrangement, kept on the server so it follows them between devices. Call it while a
 * component is being set up (it creates a query); every caller shares the same cached layout. Changes show at once
 * and are saved in order in the background.
 */
export function useDashboardLayout() {
	const queryClient = useQueryClient();

	const query = createQuery(() => ({
		queryKey: KEY,
		queryFn: async () => {
			const fromServer = await dashboardApi.layout();
			writeCache(fromServer);
			return fromServer;
		},
		placeholderData: () => readCache(),
		// a refetch while a save is still in flight would briefly bring the old order back
		staleTime: 60_000
	}));

	const save = createMutation(() => ({
		// one at a time and in order, so two quick changes can't arrive out of order and the older one win
		scope: { id: 'dashboard-layout' },
		mutationFn: (next: Layout) => dashboardApi.saveLayout(next),
		// if it didn't save, show what the server actually has rather than something that will vanish on reload
		onError: () => queryClient.invalidateQueries({ queryKey: KEY })
	}));

	const layout = $derived<Layout>(query.data ?? emptyLayout());
	const ordered = $derived(orderedWidgets(widgets, layout));
	const visible = $derived(ordered.filter((w) => !layout.hidden.includes(w.id)));

	function update(next: Layout) {
		// applied to the cache synchronously, so a dropped tile never flickers back before the save returns
		queryClient.setQueryData(KEY, next);
		writeCache(next);
		save.mutate(next);
	}

	return {
		/** False only the very first time on a device, while there's neither a cached nor a fetched layout. */
		get ready() {
			return !query.isPending || query.isError;
		},
		/** Every widget in the user's order, hidden ones included (what the customize panel lists). */
		get ordered() {
			return ordered;
		},
		get visible() {
			return visible;
		},
		get saveError(): string | null {
			return save.isError ? save.error.message : null;
		},
		isHidden: (id: string) => layout.hidden.includes(id),
		setHidden: (id: string, hide: boolean) => update({ ...layout, hidden: withHidden(layout.hidden, id, hide) }),
		/** One step up or down in the full list (the arrow buttons). */
		move: (id: string, delta: -1 | 1) =>
			update({ ...layout, order: moved(ordered.map((w) => w.id), id, delta) }),
		/** A new order of the tiles on screen (drag and drop); hidden tiles keep their places. */
		reorderVisible: (visibleIds: string[]) =>
			update({ ...layout, order: applyVisibleOrder(ordered.map((w) => w.id), layout.hidden, visibleIds) }),
		reset: () => update(emptyLayout())
	};
}

export type DashboardLayout = ReturnType<typeof useDashboardLayout>;
