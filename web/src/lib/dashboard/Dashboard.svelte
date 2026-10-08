<script lang="ts">
	import { onDestroy } from 'svelte';
	import { flip } from 'svelte/animate';
	import { prettyDate, today } from '$lib/dates';
	import DashboardCustomize from './DashboardCustomize.svelte';
	import TodayProgress from './TodayProgress.svelte';
	import WidgetCard from './WidgetCard.svelte';
	import { moveTo } from './layout';
	import { widgets } from './registry';
	import type { DashboardWidget, ProgressPart } from './types';
	import { useDashboardLayout } from './useDashboardLayout.svelte';

	const layout = useDashboardLayout();
	let customizing = $state(false);

	const hour = new Date().getHours();
	const greeting = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';

	// ---- today's overall progress: every tile that contributes, except the ones switched off ----

	const sources = widgets
		.filter((w) => w.progress)
		.map((widget) => ({ widget, read: widget.progress!(() => layout.ready && !layout.isHidden(widget.id)) }));

	const parts = $derived(
		sources.flatMap(({ widget, read }): ProgressPart[] => {
			if (layout.isHidden(widget.id)) return [];
			const progress = read();
			return progress && progress.total > 0 ? [{ id: widget.id, title: widget.title, ...progress }] : [];
		})
	);

	// ---- drag and drop ----
	// Pointer events rather than the HTML5 drag API, which doesn't work on touch screens. While dragging, the tiles
	// reorder live (`preview`) so you see where it will land; the new order is saved when you let go.

	let dragId = $state<string | null>(null);
	let preview = $state<string[] | null>(null);

	const visibleIds = $derived(layout.visible.map((w) => w.id));
	const shown = $derived.by(() => {
		const byId = new Map(layout.visible.map((w) => [w.id, w]));
		return (preview ?? visibleIds).map((id) => byId.get(id)).filter((w): w is DashboardWidget => w !== undefined);
	});

	const SCROLL_EDGE = 90;
	const SCROLL_STEP = 16;

	function startDrag(event: PointerEvent, id: string) {
		if (event.pointerType === 'mouse' && event.button !== 0) return;
		event.preventDefault();
		dragId = id;
		preview = [...visibleIds];
		// on window, not on the handle: reordering moves the tile's element in the DOM, which can drop pointer capture
		window.addEventListener('pointermove', onDragMove);
		window.addEventListener('pointerup', onDragEnd);
		window.addEventListener('pointercancel', onDragCancel);
	}

	function onDragMove(event: PointerEvent) {
		if (!dragId || !preview) return;

		// keep long dashboards reachable: nudge the page when the pointer is near the top or bottom edge
		if (event.clientY < SCROLL_EDGE) window.scrollBy(0, -SCROLL_STEP);
		else if (event.clientY > window.innerHeight - SCROLL_EDGE) window.scrollBy(0, SCROLL_STEP);

		const over = document.elementFromPoint(event.clientX, event.clientY)?.closest<HTMLElement>('[data-widget]');
		const target = over?.dataset.widget;
		if (target && target !== dragId && preview.includes(target)) preview = moveTo(preview, dragId, target);
	}

	function stopDragging() {
		window.removeEventListener('pointermove', onDragMove);
		window.removeEventListener('pointerup', onDragEnd);
		window.removeEventListener('pointercancel', onDragCancel);
		dragId = null;
		preview = null;
	}

	function onDragEnd() {
		const order = preview;
		const changed = order !== null && order.join() !== visibleIds.join();
		stopDragging();
		if (changed) layout.reorderVisible(order);
	}

	const onDragCancel = stopDragging;

	onDestroy(stopDragging);
</script>

<section aria-label="Dashboard">
	<div class="flex items-end justify-between gap-3">
		<div>
			<h1 class="text-2xl font-semibold">{greeting}</h1>
			<p class="text-sm text-slate-400">{prettyDate(today())}</p>
		</div>
		<button
			onclick={() => (customizing = !customizing)}
			aria-expanded={customizing}
			class="rounded-md border border-slate-700 px-3 py-1.5 text-sm text-slate-300 hover:bg-slate-800"
		>
			{customizing ? 'Done' : 'Customize'}
		</button>
	</div>

	{#if layout.saveError}
		<p class="mt-3 text-sm text-red-400">Couldn't save your layout: {layout.saveError}</p>
	{/if}

	{#if customizing}
		<div class="mt-4"><DashboardCustomize {layout} /></div>
	{/if}

	{#if parts.length > 0}
		<div class="mt-4"><TodayProgress {parts} /></div>
	{/if}

	{#if !layout.ready}
		<p class="mt-4 text-sm text-slate-400">Loading…</p>
	{:else if shown.length === 0}
		<p class="mt-4 rounded-lg border border-dashed border-slate-700 p-6 text-sm text-slate-400">
			Nothing is shown on your dashboard. Use Customize to bring tiles back.
		</p>
	{:else}
		<div class="mt-4 grid gap-3 md:grid-cols-2 {dragId ? 'select-none' : ''}">
			{#each shown as widget (widget.id)}
				<div animate:flip={{ duration: 200 }} class={widget.span === 2 ? 'md:col-span-2' : ''}>
					<WidgetCard
						{widget}
						editing={customizing}
						dragging={dragId === widget.id}
						onHandleDown={(event) => startDrag(event, widget.id)}
					/>
				</div>
			{/each}
		</div>
	{/if}
</section>
