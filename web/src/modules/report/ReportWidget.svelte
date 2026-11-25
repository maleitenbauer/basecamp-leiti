<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { today } from '$lib/dates';
	import { reportApi } from './api';
	import { mondayOf } from './weeks';

	const now = today();
	const weekStart = mondayOf(now);

	// the same query as the report page when it shows the current week
	const report = createQuery(() => ({
		queryKey: ['report', 'weekly', weekStart, now],
		queryFn: () => reportApi.weekly(weekStart, now)
	}));
</script>

{#if report.isPending}
	<p class="text-sm text-slate-400">Loading…</p>
{:else if report.isError}
	<p class="text-sm text-red-400">{report.error.message}</p>
{:else if report.data.sections.length === 0}
	<p class="text-sm text-slate-400">Nothing to report yet this week.</p>
{:else}
	<p class="text-xs text-slate-500">This week so far</p>
	<ul class="mt-1.5 space-y-1.5">
		{#each report.data.sections.slice(0, 4) as section (section.id)}
			<li class="text-sm">
				<span class="text-slate-400">{section.title}</span>
				<span class="text-slate-200">· {section.headline}</span>
			</li>
		{/each}
	</ul>
{/if}
