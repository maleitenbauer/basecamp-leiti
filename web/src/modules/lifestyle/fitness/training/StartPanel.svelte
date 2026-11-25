<script lang="ts">
	import { createMutation, useQueryClient } from '@tanstack/svelte-query';
	import { prettyDate, today } from '$lib/dates';
	import { showWorkout, trainingApi, trainingKey } from './api';
	import { minutesText } from './format';
	import PlanForm from './PlanForm.svelte';
	import type { Plan, TrainingOverview, Workout } from './types';

	/** Nothing is running: this week's progress, the plans to start from, and the recent workouts. */
	let { overview, knownExercises }: { overview: TrainingOverview; knownExercises: string[] } = $props();

	const queryClient = useQueryClient();

	let editing = $state<number | 'new' | null>(null);
	let error = $state('');

	const start = createMutation(() => ({
		mutationFn: (planId: number | null) => trainingApi.start(planId, today()),
		onSuccess: (workout: Workout) => {
			error = '';
			showWorkout(queryClient, workout);
		},
		onError: (e: Error) => (error = e.message)
	}));
	const removePlan = createMutation(() => ({
		mutationFn: (id: number) => trainingApi.deletePlan(id),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: trainingKey }),
		onError: (e: Error) => (error = e.message)
	}));
	const removeWorkout = createMutation(() => ({
		mutationFn: (id: number) => trainingApi.discard(id),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: trainingKey }),
		onError: (e: Error) => (error = e.message)
	}));

	const stats = $derived(overview.stats);
	const dots = $derived(
		stats.weeklyTarget === null
			? []
			: Array.from({ length: Math.max(stats.weeklyTarget, stats.workoutsThisWeek) }, (_, i) => i < stats.workoutsThisWeek)
	);

	const summary = (plan: Plan) =>
		plan.exercises
			.slice(0, 4)
			.map((e) => e.name)
			.join(', ') + (plan.exercises.length > 4 ? ` + ${plan.exercises.length - 4} more` : '');

	function lastDone(plan: Plan): string {
		if (!plan.lastDoneOn) return 'Not done yet';
		const days = Math.round((Date.parse(`${today()}T12:00:00`) - Date.parse(`${plan.lastDoneOn}T12:00:00`)) / 86_400_000);
		return days <= 0 ? 'Done today' : days === 1 ? 'Done yesterday' : `Done ${days} days ago`;
	}
</script>

<div class="space-y-5">
	<div class="rounded-lg border border-slate-700 p-3">
		<p class="text-sm text-slate-300">
			{#if dots.length > 0}
				<span class="mr-2 inline-flex gap-1 align-middle" role="img" aria-label="{stats.workoutsThisWeek} of {stats.weeklyTarget} workouts this week">
					{#each dots as filled, i (i)}
						<span class="h-2.5 w-2.5 rounded-full {filled ? 'bg-emerald-400' : 'bg-slate-700'}"></span>
					{/each}
				</span>
				{stats.workoutsThisWeek} of {stats.weeklyTarget} workouts this week
			{:else}
				{stats.workoutsThisWeek} workout{stats.workoutsThisWeek === 1 ? '' : 's'} this week
			{/if}
		</p>
		{#if stats.weeklyTarget === null && overview.plans.length > 0}
			<p class="mt-1 text-xs text-slate-500">Link a plan to a routine (like "Gym, 3 times a week") to see your weekly goal here.</p>
		{/if}
	</div>

	{#if error}<p class="text-sm text-red-400">{error}</p>{/if}

	<section class="space-y-2">
		<h3 class="text-sm font-semibold text-slate-300">Start a workout</h3>

		{#if overview.plans.length === 0 && editing !== 'new'}
			<p class="rounded-lg border border-dashed border-slate-700 p-5 text-sm text-slate-400">
				No plans yet. Create one, for example "Push day" with your exercises, sets and weights, then start it whenever you are at the gym.
			</p>
		{/if}

		{#each overview.plans as plan (plan.id)}
			{#if editing === plan.id}
				<PlanForm {plan} {knownExercises} onDone={() => (editing = null)} />
			{:else}
				<div class="rounded-lg border border-slate-700 p-3">
					<div class="flex flex-wrap items-start justify-between gap-2">
						<div class="min-w-0">
							<h4 class="font-semibold">{plan.name}</h4>
							<p class="text-xs text-slate-500">
								{plan.exercises.length} exercise{plan.exercises.length === 1 ? '' : 's'} · {lastDone(plan)}
								{#if plan.routineName}· counts for "{plan.routineName}"{/if}
							</p>
						</div>
						<button
							onclick={() => start.mutate(plan.id)}
							disabled={start.isPending}
							class="rounded-md bg-brand-500 px-4 py-2 text-sm font-semibold text-slate-950 hover:bg-brand-400 disabled:opacity-60"
						>
							Start workout
						</button>
					</div>
					{#if plan.exercises.length > 0}<p class="mt-1.5 text-sm text-slate-400">{summary(plan)}</p>{/if}
					{#if plan.note}<p class="mt-1 text-xs text-slate-500">{plan.note}</p>{/if}
					<div class="mt-2 flex gap-3 text-xs">
						<button onclick={() => (editing = plan.id)} class="text-slate-500 hover:text-slate-300">Edit</button>
						<button
							onclick={() =>
								confirm(`Delete the plan "${plan.name}"? Workouts you did from it stay in your history.`) &&
								removePlan.mutate(plan.id)}
							class="text-slate-500 hover:text-red-400"
						>
							Delete
						</button>
					</div>
				</div>
			{/if}
		{/each}

		{#if editing === 'new'}
			<PlanForm {knownExercises} onDone={() => (editing = null)} />
		{:else}
			<div class="flex flex-wrap gap-2">
				<button onclick={() => (editing = 'new')} class="rounded-md bg-slate-800 px-3 py-2 text-sm hover:bg-slate-700">+ New plan</button>
				<button
					onclick={() => start.mutate(null)}
					disabled={start.isPending}
					class="rounded-md border border-slate-700 px-3 py-2 text-sm text-slate-300 hover:bg-slate-800"
				>
					Empty workout
				</button>
			</div>
		{/if}
	</section>

	{#if overview.recent.length > 0}
		<section>
			<h3 class="mb-2 text-sm font-semibold text-slate-300">Recent workouts</h3>
			<ul class="divide-y divide-slate-800 rounded-lg border border-slate-700 text-sm">
				{#each overview.recent as workout (workout.id)}
					<li class="flex items-center gap-3 px-3 py-2">
						<div class="min-w-0 flex-1">
							<p class="truncate">{workout.name}</p>
							<p class="text-xs text-slate-500">
								{prettyDate(workout.day)} · {minutesText(workout.minutes)} · {workout.sets} sets
								{#if workout.endedBy === 'AUTO'}· <span class="text-amber-400">ended automatically</span>{/if}
							</p>
						</div>
						<button
							onclick={() => confirm('Remove this workout from your history?') && removeWorkout.mutate(workout.id)}
							aria-label="Remove workout"
							class="text-slate-600 hover:text-red-400"
						>
							✕
						</button>
					</li>
				{/each}
			</ul>
		</section>
	{/if}
</div>
