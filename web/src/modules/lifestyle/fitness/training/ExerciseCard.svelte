<script lang="ts">
	import { createMutation, useQueryClient } from '@tanstack/svelte-query';
	import { untrack } from 'svelte';
	import { shiftDate, today, weekday } from '$lib/dates';
	import { showWorkout, trainingApi } from './api';
	import { kg, setText } from './format';
	import type { Workout, WorkoutExercise } from './types';

	/** One exercise of the running workout: what was planned, what happened last time, and the sets done so far. */
	let {
		workout,
		exercise,
		onLogged
	}: { workout: Workout; exercise: WorkoutExercise; onLogged: (restSeconds: number | null) => void } = $props();

	const queryClient = useQueryClient();

	let reps = $state('');
	let weight = $state('');
	let editingId = $state<number | null>(null);
	let editReps = $state('');
	let editWeight = $state('');
	let error = $state('');

	/** The next set starts from the last one just done, else from last time, else from the plan. */
	function prefill() {
		const done = exercise.sets;
		const last = done.at(-1);
		const before = exercise.previous?.sets[done.length] ?? exercise.previous?.sets.at(-1);
		const r = last?.reps ?? before?.reps ?? exercise.targetReps;
		const w = last ? last.weightKg : (before?.weightKg ?? exercise.targetWeightKg);
		reps = r ? String(r) : '';
		weight = w ? String(w) : '';
	}

	// start over after each set was logged or removed, but never while the user is typing in between
	$effect(() => {
		void exercise.sets.length;
		untrack(prefill);
	});

	function parse(repsText: string, weightText: string) {
		const r = Math.round(Number(repsText));
		const w = weightText.trim() === '' ? null : Number(weightText.replace(',', '.'));
		if (!(r >= 1 && r <= 500) || (w !== null && !(w >= 0 && w <= 1000))) return null;
		return { reps: r, weightKg: w };
	}

	const log = createMutation(() => ({
		mutationFn: (set: { reps: number; weightKg: number | null }) => trainingApi.logSet(workout.id, exercise.id, set),
		onSuccess: (updated: Workout) => {
			error = '';
			showWorkout(queryClient, updated);
			onLogged(exercise.restSeconds);
		},
		onError: (e: Error) => (error = e.message)
	}));
	const update = createMutation(() => ({
		mutationFn: (v: { id: number; reps: number; weightKg: number | null }) =>
			trainingApi.updateSet(workout.id, v.id, { reps: v.reps, weightKg: v.weightKg }),
		onSuccess: (updated: Workout) => {
			error = '';
			showWorkout(queryClient, updated);
			editingId = null;
		},
		onError: (e: Error) => (error = e.message)
	}));
	const remove = createMutation(() => ({
		mutationFn: (id: number) => trainingApi.deleteSet(workout.id, id),
		onSuccess: (updated: Workout) => {
			error = '';
			showWorkout(queryClient, updated);
		},
		onError: (e: Error) => (error = e.message)
	}));

	function submit(event: SubmitEvent) {
		event.preventDefault();
		const set = parse(reps, weight);
		if (!set) {
			error = 'Enter the reps, and the weight if there is one.';
			return;
		}
		log.mutate(set);
	}

	function submitEdit(event: SubmitEvent, id: number) {
		event.preventDefault();
		const set = parse(editReps, editWeight);
		if (!set) {
			error = 'Enter the reps, and the weight if there is one.';
			return;
		}
		update.mutate({ id, ...set });
	}

	function startEdit(set: { id: number; reps: number; weightKg: number | null }) {
		editingId = set.id;
		editReps = String(set.reps);
		editWeight = set.weightKg ? String(set.weightKg) : '';
	}

	const target = $derived(
		exercise.targetSets && exercise.targetReps
			? `${exercise.targetSets} × ${exercise.targetReps}${exercise.targetWeightKg ? ` @ ${kg(exercise.targetWeightKg)}` : ''}`
			: null
	);
	const planned = $derived(exercise.targetSets ?? 0);
	const lastDay = $derived(
		exercise.previous ? (exercise.previous.day === shiftDate(today(), -1) ? 'yesterday' : weekday(exercise.previous.day)) : ''
	);
</script>

<section class="rounded-lg border border-slate-700 p-3">
	<div class="flex flex-wrap items-baseline justify-between gap-x-3">
		<h3 class="font-semibold">{exercise.name}</h3>
		{#if target}<span class="text-sm text-slate-400">{target}</span>{/if}
	</div>

	{#if exercise.previous}
		<p class="mt-0.5 text-xs text-slate-500">
			Last time ({lastDay}): {exercise.previous.sets.map(setText).join(', ')}
		</p>
	{/if}

	{#if planned > 0}
		<div class="mt-2 flex gap-1" role="img" aria-label="{Math.min(exercise.sets.length, planned)} of {planned} planned sets done">
			{#each Array.from({ length: planned }, (_, i) => i) as i (i)}
				<span class="h-1.5 flex-1 rounded-full {i < exercise.sets.length ? 'bg-emerald-400' : 'bg-slate-700'}"></span>
			{/each}
		</div>
	{/if}

	{#if exercise.sets.length > 0}
		<ol class="mt-2 divide-y divide-slate-800 text-sm">
			{#each exercise.sets as set, i (set.id)}
				<li class="py-1.5">
					{#if editingId === set.id}
						<form onsubmit={(e) => submitEdit(e, set.id)} class="flex flex-wrap items-center gap-2">
							<span class="w-12 text-slate-500">Set {i + 1}</span>
							<input bind:value={editReps} inputmode="numeric" aria-label="Reps" class="w-16 rounded bg-slate-800 px-2 py-1" />
							<span class="text-slate-500">×</span>
							<input
								bind:value={editWeight}
								inputmode="decimal"
								placeholder="kg"
								aria-label="Weight in kg"
								class="w-20 rounded bg-slate-800 px-2 py-1"
							/>
							<button type="submit" disabled={update.isPending} class="text-brand-400 hover:text-brand-300">Save</button>
							<button type="button" onclick={() => (editingId = null)} class="text-slate-400 hover:text-slate-200">Cancel</button>
						</form>
					{:else}
						<div class="flex items-center gap-2">
							<span class="w-12 text-slate-500">Set {i + 1}</span>
							<span class="tabular-nums">{setText(set)}</span>
							<button onclick={() => startEdit(set)} class="ml-auto text-xs text-slate-500 hover:text-slate-300">Edit</button>
							<button
								onclick={() => remove.mutate(set.id)}
								disabled={remove.isPending}
								aria-label="Remove set {i + 1}"
								class="text-slate-500 hover:text-red-400"
							>
								✕
							</button>
						</div>
					{/if}
				</li>
			{/each}
		</ol>
	{/if}

	<form onsubmit={submit} class="mt-3 flex flex-wrap items-end gap-2">
		<label class="text-xs text-slate-400">
			Reps
			<input bind:value={reps} inputmode="numeric" class="mt-0.5 block w-16 rounded bg-slate-800 px-2 py-1.5 text-base text-slate-100" />
		</label>
		<label class="text-xs text-slate-400">
			kg
			<input
				bind:value={weight}
				inputmode="decimal"
				placeholder="body"
				class="mt-0.5 block w-20 rounded bg-slate-800 px-2 py-1.5 text-base text-slate-100"
			/>
		</label>
		<button
			type="submit"
			disabled={log.isPending}
			class="rounded-md bg-brand-500 px-4 py-2 text-sm font-semibold text-slate-950 hover:bg-brand-400 disabled:opacity-60"
		>
			✓ Log set {exercise.sets.length + 1}
		</button>
	</form>
	{#if error}<p class="mt-1 text-sm text-red-400">{error}</p>{/if}
</section>
