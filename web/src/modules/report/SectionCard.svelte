<script lang="ts">
	import { findModule } from '$modules/registry';
	import DayBars from './DayBars.svelte';
	import type { ReportSection } from './types';

	let { section }: { section: ReportSection } = $props();

	const icon = $derived(findModule(section.moduleId)?.icon);
</script>

<section class="rounded-lg border border-slate-700 p-4" aria-label={section.title} data-section={section.id}>
	<div class="flex items-start gap-2">
		<span aria-hidden="true" class="text-lg leading-6">{icon}</span>
		<div class="min-w-0">
			<h2 class="font-medium">{section.title}</h2>
			<p class="text-sm text-slate-300">{section.headline}</p>
		</div>
	</div>

	{#if section.stats.length > 0}
		<dl class="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 sm:grid-cols-3">
			{#each section.stats as stat (stat.label)}
				<div>
					<dt class="text-xs text-slate-500">{stat.label}</dt>
					<dd class="text-sm font-medium tabular-nums">
						{stat.value}
						{#if stat.detail}<span class="text-xs font-normal text-slate-500">{stat.detail}</span>{/if}
					</dd>
				</div>
			{/each}
		</dl>
	{/if}

	{#if section.bars}
		<div class="mt-4"><DayBars chart={section.bars} /></div>
	{/if}

	{#if section.highlights.length > 0}
		<ul class="mt-3 space-y-1 border-t border-slate-800 pt-3 text-sm text-slate-400">
			{#each section.highlights as line, i (i)}
				<li>{line}</li>
			{/each}
		</ul>
	{/if}
</section>
