<script lang="ts">
	import type { ReportBars } from './types';

	let { chart }: { chart: ReportBars } = $props();

	// The plot is a fixed height and each bar is a pixel height on the same scale as the goal line, so the line sits
	// exactly where its value would. The 18px above the tallest bar is room for its number.
	const PLOT = 80;
	const LABEL_ROOM = 18;

	// the goal has to fit inside the plot too, so it counts towards the scale
	const max = $derived(Math.max(1, chart.goal ?? 0, ...chart.bars.map((b) => b.value)));
	const px = (value: number) => Math.round((value / max) * PLOT);
</script>

<figure aria-label={chart.title}>
	<figcaption class="mb-1 text-xs text-slate-500">{chart.title}</figcaption>

	<div class="relative" style="height: {PLOT + LABEL_ROOM}px">
		<div class="absolute inset-0 flex items-end gap-1.5">
			{#each chart.bars as bar (bar.label)}
				<div class="flex flex-1 flex-col items-center justify-end gap-0.5">
					<span class="text-[10px] leading-3 text-slate-400 tabular-nums">{bar.display}</span>
					{#if bar.future}
						<div class="w-full rounded-t border border-dashed border-slate-700" style="height: 3px"></div>
					{:else}
						<div
							class="w-full rounded-t {bar.value > 0 ? 'bg-brand-400' : 'bg-slate-800'}"
							style="height: {Math.max(bar.value > 0 ? 3 : 2, px(bar.value))}px"
							title="{bar.label}: {bar.display || 'none'}"
						></div>
					{/if}
				</div>
			{/each}
		</div>

		{#if chart.goal !== null}
			<div class="pointer-events-none absolute inset-x-0 border-t border-dashed border-amber-400/70" style="bottom: {px(chart.goal)}px">
				<span class="absolute right-0 bottom-0 rounded bg-slate-900/80 px-1 text-[10px] text-amber-400">limit {chart.goal}</span>
			</div>
		{/if}
	</div>

	<div class="mt-1 flex gap-1.5">
		{#each chart.bars as bar (bar.label)}
			<span class="flex-1 text-center text-[10px] {bar.future ? 'text-slate-600' : 'text-slate-500'}">{bar.label}</span>
		{/each}
	</div>
</figure>
