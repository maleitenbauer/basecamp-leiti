<script lang="ts">
	import { findModule } from '$modules/registry';
	import type { DashboardWidget } from './types';

	let {
		widget,
		editing = false,
		dragging = false,
		onHandleDown
	}: {
		widget: DashboardWidget;
		/** Shows the drag handle. */
		editing?: boolean;
		/** This tile is the one being dragged right now. */
		dragging?: boolean;
		onHandleDown?: (event: PointerEvent) => void;
	} = $props();

	const module = $derived(findModule(widget.moduleId));
	const Body = $derived(widget.component);
</script>

<section
	class="rounded-lg border p-4 transition-shadow {dragging
		? 'border-brand-400 shadow-lg ring-2 ring-brand-400/40'
		: 'border-slate-700'}"
	data-widget={widget.id}
>
	<div class="flex items-center gap-2">
		{#if editing}
			<!-- touch-action none: dragging this must move the tile, not scroll the page -->
			<button
				onpointerdown={onHandleDown}
				aria-label="Drag to reorder {widget.title}"
				title="Drag to reorder"
				class="-ml-1 cursor-grab touch-none rounded px-1 text-lg leading-none text-slate-500 select-none hover:text-slate-200 {dragging
					? 'cursor-grabbing text-brand-300'
					: ''}"
			>
				⠿
			</button>
		{/if}
		<a href={widget.href} class="group flex min-w-0 flex-1 items-center gap-2">
			<span aria-hidden="true">{module?.icon}</span>
			<h2 class="truncate font-medium group-hover:text-brand-300">{widget.title}</h2>
			<span class="ml-auto text-slate-600 group-hover:text-slate-300" aria-hidden="true">→</span>
		</a>
	</div>

	<div class="mt-3">
		<!-- a widget that throws while rendering only loses its own tile, never the dashboard -->
		<svelte:boundary>
			<Body />
			{#snippet failed(_error, reset)}
				<p class="text-sm text-red-400">This tile couldn't be shown.</p>
				<button onclick={reset} class="mt-1 text-sm text-brand-400 hover:text-brand-300">Try again</button>
			{/snippet}
		</svelte:boundary>
	</div>
</section>
