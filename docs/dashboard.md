# Home dashboard

The home page (`/`) is a dashboard: a **"Today" progress bar** that adds up what's done across modules, then a tile per
module — open routines, todos due, calories left, shopping lists, CS2 practice. Modules themselves are reached through
the top navigation.

**Customize** lets you drag tiles into order (by the ⠿ handle), turn tiles off, or move them with arrow buttons. The
arrangement is saved **to your account**, so it's the same on every device.

## Adding a tile for a new module (or submodule)

Two files, both inside your module's folder. Nothing else to edit — no central list, no registration, and no backend
change (the server stores widget ids it knows nothing about).

**1. The tile body**, e.g. `web/src/modules/lifestyle/habits/HabitsWidget.svelte`. It fetches its own data the way a
page does, and handles its own loading / empty / error states:

```svelte
<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { habitsApi } from './api';

	const habits = createQuery(() => ({
		queryKey: ['lifestyle', 'habits'], // reuse the page's key and both stay in sync
		queryFn: () => habitsApi.list()
	}));
</script>

{#if habits.isPending}
	<p class="text-sm text-slate-400">Loading…</p>
{:else if habits.isError}
	<p class="text-sm text-red-400">{habits.error.message}</p>
{:else}
	<!-- … -->
{/if}
```

**2. The registration**, `web/src/modules/lifestyle/habits/habits.widget.ts` — any file named `*.widget.ts` under
`src/modules/` is found automatically:

```ts
import { defineWidget } from '$lib/dashboard/types';
import HabitsWidget from './HabitsWidget.svelte';

export default defineWidget({
	id: 'lifestyle.habits', // unique and stable: saved layouts refer to it, so don't rename it later
	title: 'Habits',
	moduleId: 'lifestyle', // supplies the icon
	href: '/lifestyle/habits', // where the tile's title links to
	order: 35, // default position; existing ones use 10, 20, 30… so there's room between them
	// span: 2,   // optional: take two columns on wide screens
	component: HabitsWidget
});
```

That's it: the tile appears on the dashboard and in Customize. For a user who has already arranged their dashboard, a
new tile shows up **after** their placed ones and visible, so nothing they set up gets shuffled.

### Counting towards the "Today" bar (optional)

If the module has things that are *done* or *not done* today, add a `progress` to the registration. It's a small
function that creates the query (same key as the tile and the page, so it's shared) and returns a reader:

```ts
// progress.ts
export function useHabitProgress(enabled: () => boolean): () => TodayProgress | null {
	const habits = createQuery(() => ({ queryKey: ['lifestyle', 'habits'], queryFn: () => habitsApi.list(), enabled: enabled() }));
	return () => (habits.data ? { done: habits.data.doneToday, total: habits.data.dueToday } : null);
}

// habits.widget.ts:  progress: useHabitProgress
```

Return `null` while loading or if there's nothing to do; a module with `total: 0` simply doesn't appear in the breakdown.
`enabled` is false while the user has the tile hidden — then it neither counts nor makes requests. Only things with a
real done state belong here, which is why **calories and shopping don't contribute**: a calorie budget isn't "done",
and a shopping list isn't a today thing.

Currently counted: **routines** (open + done today), **todos** (overdue or due today and still open + finished today),
**CS2 practice** (every active item). Each item weighs the same, so a module with many items moves the bar more.

### What the framework guarantees

- **Isolation.** Every tile fetches independently, so a slow or failing module never holds up the others, and a tile
  that throws while rendering only loses itself (`<svelte:boundary>` with a "Try again"). Tiles should still show
  `query.isError` themselves, since a failed request isn't a render error.
- **Duplicate ids fail loudly** at load time instead of two tiles silently sharing one saved position.
- **Shared cache.** A tile that uses the same TanStack query key as its page shares the cache, so checking off a routine
  on the dashboard updates the Routine page and vice versa.

### Conventions worth keeping

- Keep a tile **glanceable**: a number or a short list with a "+ N more" link, not the module's whole UI.
- Quick actions are welcome where they are one tap and safe (the routines tile can check something off).
- Put the tile's data on the module's existing API. Add an endpoint only if the page's API genuinely doesn't have what
  the tile needs.

## How it works

Frontend (`web/src/lib/dashboard/`):

- `registry.ts` — finds every `*.widget.ts` with `import.meta.glob` (eager, at build time) and sorts by `order`.
- `layout.ts` — pure functions for ordering, hiding, moving and dragging, with no Svelte in them.
- `useDashboardLayout.svelte.ts` — the user's arrangement as a TanStack query + mutation. Changes show at once (the cache
  is updated synchronously) and are saved in order, one at a time, in the background; if a save fails, the server's copy
  is fetched again so the screen never shows something that will vanish on reload. A copy in `localStorage` is only a
  cache so the first paint is already in the right order; the server's layout always wins.
- `Dashboard.svelte` (drag and drop, overall progress), `WidgetCard.svelte` (card, handle, error boundary),
  `DashboardCustomize.svelte`, `TodayProgress.svelte`.

Backend (`com.markus.basecamp.core.dashboard`): `GET`/`PUT /api/dashboard/layout`, one row per user in
`core.dashboard_layout` holding `{"order": [...], "hidden": [...]}`. The server only checks that ids are well-formed
and the lists are bounded, and drops duplicates — it never needs to know which widgets exist.

### Drag and drop

Built on pointer events, not the HTML5 drag API (which doesn't work on touch screens, and this is a PWA). Grab the ⠿
handle in Customize mode; the tiles reorder live and glide into place, the page scrolls when you drag near its top or
bottom edge, and the new order is saved when you let go. Tiles you've hidden keep their positions in the saved order.
The arrow buttons in the Customize panel do the same job for keyboard users.

## Ways to make it more dynamic later

1. **Per-tile settings** (medium). A tile declares a small settings schema ("how many items to show", "which list") and
   Customize renders a form from it; stored alongside the layout.
2. **Resizing** (small–medium). `span` becomes something the user sets per tile.
3. **Conditional tiles** (small). A `visible()` hook on a widget, for example "only if a FACEIT nickname is set", so
   unconfigured modules don't show empty tiles.
4. **Default-hidden tiles** (small). For tiles that are opt-in rather than shown to everyone.
5. **A backend contributor interface** (medium–large). Like `Notifier` for notifications, modules implement a
   `DashboardContributor` and `/api/dashboard` returns every summary in one request. It saves round trips and allows
   server-side logic (ranking what's most urgent across modules), at the cost of coupling summaries to the backend.
6. **A cross-module "needs attention" strip** (medium). The progress bar says how much is done; this would say what to
   do next, ranked across modules.
7. **Weighted or per-module progress** (small). Today every item counts the same; modules could declare a weight.
