<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { today } from '$lib/dates';
	import ActiveWorkout from './training/ActiveWorkout.svelte';
	import { overviewKey, trainingApi } from './training/api';
	import FinishSummary from './training/FinishSummary.svelte';
	import StartPanel from './training/StartPanel.svelte';
	import type { FinishResult } from './training/types';

	const date = today();

	const overview = createQuery(() => ({
		queryKey: overviewKey(date),
		queryFn: () => trainingApi.overview(date),
		// a workout can be ended automatically while this page is open, so check back now and then
		refetchInterval: (query) => (query.state.data?.active ? 60_000 : false)
	}));

	// kept here, not in the running workout: that one disappears the moment the workout is saved
	let finished = $state<FinishResult | null>(null);

	const knownExercises = $derived([
		...new Set((overview.data?.plans ?? []).flatMap((plan) => plan.exercises.map((exercise) => exercise.name)))
	]);
</script>

<main class="mx-auto max-w-2xl space-y-4 p-4 sm:p-6">
	<h2 class="text-lg font-semibold">Training</h2>

	{#if overview.isPending}
		<p class="text-slate-400">Loading…</p>
	{:else if overview.isError}
		<p class="text-red-400">Could not load your training: {overview.error.message}</p>
	{:else if finished}
		<FinishSummary result={finished} onClose={() => (finished = null)} />
	{:else if overview.data.active}
		<ActiveWorkout
			workout={overview.data.active}
			stats={overview.data.stats}
			{knownExercises}
			onFinished={(result) => (finished = result)}
		/>
	{:else}
		<StartPanel overview={overview.data} {knownExercises} />
	{/if}
</main>
