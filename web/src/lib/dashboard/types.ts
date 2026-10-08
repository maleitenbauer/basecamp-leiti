import type { Component } from 'svelte';

/** What a module has to do today: [total] things, [done] of them finished. They are added up into the overall bar. */
export interface TodayProgress {
	done: number;
	total: number;
}

/** One module's share of the overall bar, labelled for the little breakdown under it. */
export interface ProgressPart extends TodayProgress {
	id: string;
	title: string;
}

/**
 * One tile on the home dashboard. A module contributes one by putting a `<name>.widget.ts` file anywhere under
 * src/modules/ that default-exports `defineWidget({...})` — the dashboard finds it by itself, nothing else to edit.
 * See docs/dashboard.md.
 */
export interface DashboardWidget {
	/** Unique and stable: it's what a user's saved layout refers to, so don't rename it later. e.g. 'lifestyle.routine' */
	id: string;
	title: string;
	/** The module's id in the registry; supplies the tile's icon. */
	moduleId: string;
	/** Where the tile's title links to. */
	href: string;
	/** Default position (lower comes first). Leave gaps (10, 20, 30…) so later widgets can slot in between. */
	order: number;
	/** How many columns it takes on wide screens. Default 1. */
	span?: 1 | 2;
	/**
	 * Renders the tile's body (the card around it is provided). It fetches its own data, the same way a page does,
	 * and shows its own loading / empty / error states — so one slow or failing module never holds up the others.
	 */
	component: Component;
	/**
	 * Optional: lets this tile count towards the overall "done today" bar. A function that is called once while the
	 * dashboard is being set up — like a component's init, so it may create queries — and returns a function that reads
	 * today's progress (null while it's still loading, or if there is nothing to do). [enabled] is false while the user
	 * has this tile hidden, so a hidden module neither counts nor makes requests. Only things that have a "done" state
	 * belong here (routines, todos); a budget like calories doesn't.
	 */
	progress?: (enabled: () => boolean) => () => TodayProgress | null;
}

/** Just an identity function: it makes the compiler check the shape of a widget file. */
export const defineWidget = (widget: DashboardWidget): DashboardWidget => widget;
