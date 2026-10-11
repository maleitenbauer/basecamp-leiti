<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { shiftDate, today } from '$lib/dates';
	import { weeklyGreeting } from '$lib/greetings';
	import { reportApi } from './api';
	import SectionCard from './SectionCard.svelte';
	import { formatWeek, mondayOf } from './weeks';

	const now = today();
	const currentWeek = mondayOf(now);
	let weekStart = $state(currentWeek);

	const report = createQuery(() => ({
		queryKey: ['report', 'weekly', weekStart, now],
		queryFn: () => reportApi.weekly(weekStart, now)
	}));

	const greeting = $derived(
		report.data
			? weeklyGreeting({ weekStart, score: report.data.score, inProgress: report.data.inProgress })
			: null
	);

	const nav = 'rounded border border-slate-700 px-2 py-1 text-sm hover:bg-slate-800 disabled:opacity-30 disabled:hover:bg-transparent';
</script>

<main class="mx-auto max-w-4xl space-y-4 p-4 sm:p-6">
	<div class="flex flex-wrap items-end justify-between gap-3">
		<div>
			{#if greeting}
				<h1 class="text-2xl font-semibold">{greeting.headline}</h1>
				<p class="mt-1 text-slate-300">{greeting.line}</p>
			{:else}
				<h1 class="text-2xl font-semibold">Your week</h1>
			{/if}
			<p class="mt-1 text-sm text-slate-400">
				{formatWeek(weekStart)}{#if weekStart === currentWeek} · in progress{/if}
			</p>
		</div>

		<div class="flex items-center gap-1">
			<button onclick={() => (weekStart = shiftDate(weekStart, -7))} class={nav} title="Previous week">◀</button>
			<button
				onclick={() => (weekStart = shiftDate(weekStart, 7))}
				disabled={weekStart >= currentWeek}
				class={nav}
				title="Next week"
			>
				▶
			</button>
			{#if weekStart !== currentWeek}
				<button onclick={() => (weekStart = currentWeek)} class="ml-1 text-sm text-brand-400 hover:text-brand-300">
					This week
				</button>
			{/if}
		</div>
	</div>

	{#if report.isPending}
		<p class="text-slate-400">Loading…</p>
	{:else if report.isError}
		<p class="text-red-400">Could not load the report: {report.error.message}</p>
	{:else}
		{#if report.data.inProgress}
			<p class="text-xs text-slate-500">
				This week isn't over yet, so the figures only cover the days so far.
			</p>
		{/if}

		{#if report.data.sections.length === 0}
			<p class="rounded-lg border border-dashed border-slate-700 p-6 text-sm text-slate-400">
				Nothing to report for this week. As you use routines, todos, nutrition and the rest, they show up here.
			</p>
		{:else}
			<div class="space-y-3">
				{#each report.data.sections as section (section.id)}
					<SectionCard {section} />
				{/each}
			</div>
		{/if}
	{/if}
</main>
