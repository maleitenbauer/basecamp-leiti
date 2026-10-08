import type { DashboardWidget } from './types';

/** What a user has chosen. Both lists hold widget ids; ids of widgets that no longer exist are simply ignored. */
export interface Layout {
	order: string[];
	hidden: string[];
}

export const emptyLayout = (): Layout => ({ order: [], hidden: [] });

/**
 * All widgets in the user's order. Ones they have never placed (for example a module added after they arranged
 * their dashboard) keep their default order and go after the placed ones — so a new tile shows up without
 * rearranging what they already have.
 */
export function orderedWidgets(all: DashboardWidget[], layout: Layout): DashboardWidget[] {
	const byId = new Map(all.map((w) => [w.id, w]));
	const placed = layout.order.map((id) => byId.get(id)).filter((w): w is DashboardWidget => w !== undefined);
	const placedIds = new Set(placed.map((w) => w.id));
	return [...placed, ...all.filter((w) => !placedIds.has(w.id))];
}

/** The widgets to show: the ordered ones minus the ones the user hid. New widgets are visible by default. */
export function visibleWidgets(all: DashboardWidget[], layout: Layout): DashboardWidget[] {
	const hidden = new Set(layout.hidden);
	return orderedWidgets(all, layout).filter((w) => !hidden.has(w.id));
}

/** The full order with [id] moved one step up (-1) or down (+1); unchanged at either end. */
export function moved(ids: string[], id: string, delta: -1 | 1): string[] {
	const from = ids.indexOf(id);
	const to = from + delta;
	if (from === -1 || to < 0 || to >= ids.length) return ids;
	const next = [...ids];
	[next[from], next[to]] = [next[to], next[from]];
	return next;
}

/**
 * [ids] with [id] moved to where [targetId] currently is — what dragging a tile over another one means. Dragging
 * forward puts it after the target, backward before it, so it always lands where the pointer is.
 */
export function moveTo(ids: string[], id: string, targetId: string): string[] {
	const from = ids.indexOf(id);
	const to = ids.indexOf(targetId);
	if (from === -1 || to === -1 || from === to) return ids;
	const next = ids.filter((x) => x !== id);
	next.splice(to, 0, id);
	return next;
}

/**
 * Turns a new order of just the visible tiles into a full order: hidden tiles keep their slots, and the visible ones
 * fill the remaining slots in the new order. (Dragging only ever rearranges what's on screen.)
 */
export function applyVisibleOrder(fullIds: string[], hidden: string[], visibleIds: string[]): string[] {
	const hiddenSet = new Set(hidden);
	const queue = [...visibleIds];
	return fullIds.map((id) => (hiddenSet.has(id) ? id : (queue.shift() ?? id)));
}

export function withHidden(hidden: string[], id: string, hide: boolean): string[] {
	const without = hidden.filter((h) => h !== id);
	return hide ? [...without, id] : without;
}

/** Parses whatever is in storage; anything unexpected becomes an empty layout rather than an error. */
export function parseLayout(raw: string | null): Layout {
	if (!raw) return emptyLayout();
	try {
		const data = JSON.parse(raw) as Partial<Layout>;
		const strings = (value: unknown): string[] =>
			Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : [];
		return { order: strings(data.order), hidden: strings(data.hidden) };
	} catch {
		return emptyLayout();
	}
}
