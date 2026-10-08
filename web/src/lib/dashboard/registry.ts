import type { DashboardWidget } from './types';

/**
 * Every `*.widget.ts` under src/modules/ is picked up here at build time (eager, so it ships with the page that
 * needs it). That's the whole registration step: add a file, get a tile.
 */
const found = import.meta.glob<{ default: DashboardWidget }>('/src/modules/**/*.widget.ts', { eager: true });

function collect(): DashboardWidget[] {
	const seen = new Map<string, string>();
	const result: DashboardWidget[] = [];
	for (const [path, mod] of Object.entries(found)) {
		const widget = mod.default;
		if (!widget?.id) {
			throw new Error(`${path} must default-export defineWidget({...})`);
		}
		const clash = seen.get(widget.id);
		if (clash) {
			// two tiles with one id would silently share a saved position and visibility
			throw new Error(`Dashboard widget id '${widget.id}' is used by both ${clash} and ${path}`);
		}
		seen.set(widget.id, path);
		result.push(widget);
	}
	return result.sort((a, b) => a.order - b.order || a.id.localeCompare(b.id));
}

/** All widgets, in their default order. */
export const widgets: DashboardWidget[] = collect();
