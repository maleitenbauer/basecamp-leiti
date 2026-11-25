<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { today } from '$lib/dates';
	import { overviewKey, trainingApi } from './api';
	import { clock } from './format';
	import { useNow } from './now.svelte';

	const date = today();
	const now = useNow();

	// the same query as the Training page
	const overview = createQuery(() => ({
		queryKey: overviewKey(date),
		queryFn: () => trainingApi.overview(date)
	}));

	const active = $derived(overview.data?.active ?? null);
	const stats = $derived(overview.data?.stats ?? null);
	const elapsed = $derived(active ? Math.max(0, (now.value - Date.parse(active.startedAt)) / 1000) : 0);
</script>

{#if overview.isPending}
	<p class="text-sm text-slate-400">Loading…</p>
{:else if overview.isError}
	<p class="text-sm text-red-400">{overview.error.message}</p>
{:else if active}
	<p class="text-sm text-slate-300">{active.name} is running</p>
	<p class="mt-1 text-3xl font-semibold tabular-nums text-brand-400">{clock(elapsed)}</p>
	<a href="/lifestyle/fitness/training" class="mt-1 inline-block text-sm text-brand-400 hover:text-brand-300">Back to the workout</a>
{:else if overview.data.plans.length === 0}
	<p class="text-sm text-slate-400">
		No workout plan yet. <a href="/lifestyle/fitness/training" class="text-brand-400 hover:text-brand-300">Create one</a>
	</p>
{:else if stats}
	<p class="text-sm text-slate-300">
		{#if stats.weeklyTarget !== null}
			{stats.workoutsThisWeek} of {stats.weeklyTarget} workouts this week
		{:else}
			{stats.workoutsThisWeek} workout{stats.workoutsThisWeek === 1 ? '' : 's'} this week
		{/if}
	</p>
	<a href="/lifestyle/fitness/training" class="mt-1 inline-block text-sm text-brand-400 hover:text-brand-300">Start a workout</a>
{/if}
