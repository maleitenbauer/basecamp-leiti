<script lang="ts">
	import { findModule } from '$modules/registry';
	import type { DashboardLayout } from './useDashboardLayout.svelte';

	let { layout }: { layout: DashboardLayout } = $props();

	const all = $derived(layout.ordered);
</script>

<section class="rounded-lg border border-slate-700 p-4" aria-label="Customize dashboard">
	<div class="flex items-center justify-between gap-3">
		<h2 class="text-sm font-semibold">Customize</h2>
		<button onclick={() => layout.reset()} class="text-xs text-slate-500 hover:text-slate-200">
			Reset to default
		</button>
	</div>
	<p class="mt-1 text-xs text-slate-500">
		Drag a tile by its handle (⠿) to move it. Here you can also turn tiles off, or use the arrows. It's saved to your
		account, so it's the same on every device.
	</p>

	<ul class="mt-3 divide-y divide-slate-800">
		{#each all as widget, i (widget.id)}
			<li class="flex items-center gap-3 py-2">
				<label class="flex min-w-0 flex-1 items-center gap-3 text-sm">
					<input
						type="checkbox"
						checked={!layout.isHidden(widget.id)}
						onchange={(e) => layout.setHidden(widget.id, !e.currentTarget.checked)}
						class="h-4 w-4"
					/>
					<span aria-hidden="true">{findModule(widget.moduleId)?.icon}</span>
					<span class="truncate {layout.isHidden(widget.id) ? 'text-slate-500' : ''}">{widget.title}</span>
				</label>
				<div class="flex gap-1">
					<button
						onclick={() => layout.move(widget.id, -1)}
						disabled={i === 0}
						class="rounded border border-slate-700 px-2 py-0.5 text-xs hover:bg-slate-800 disabled:opacity-30 disabled:hover:bg-transparent"
						aria-label="Move {widget.title} up"
					>
						▲
					</button>
					<button
						onclick={() => layout.move(widget.id, 1)}
						disabled={i === all.length - 1}
						class="rounded border border-slate-700 px-2 py-0.5 text-xs hover:bg-slate-800 disabled:opacity-30 disabled:hover:bg-transparent"
						aria-label="Move {widget.title} down"
					>
						▼
					</button>
				</div>
			</li>
		{/each}
	</ul>
</section>
