<script lang="ts">
	import { createMutation, createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { untrack } from 'svelte';
	import { today } from '$lib/dates';
	import { routineApi } from '../../routine/api';
	import { trainingApi, trainingKey } from './api';
	import type { Plan, PlanInput } from './types';

	/** Creates a workout plan, or edits [plan] when given. [onDone] runs after a successful save or on cancel. */
	let { plan, knownExercises, onDone }: { plan?: Plan; knownExercises: string[]; onDone: () => void } = $props();

	interface Row {
		key: number;
		name: string;
		sets: string;
		reps: string;
		weight: string;
		rest: string;
	}

	const queryClient = useQueryClient();

	let nextKey = 0;
	const blankRow = (): Row => ({ key: nextKey++, name: '', sets: '3', reps: '10', weight: '', rest: '' });

	// the form starts from the plan's current values on purpose; it isn't kept in sync afterwards
	let name = $state(untrack(() => plan?.name ?? ''));
	let note = $state(untrack(() => plan?.note ?? ''));
	let routineId = $state<string>(untrack(() => (plan?.routineId ? String(plan.routineId) : '')));
	let rows = $state<Row[]>(
		untrack(() =>
			plan && plan.exercises.length > 0
				? plan.exercises.map((e) => ({
						key: nextKey++,
						name: e.name,
						sets: String(e.sets),
						reps: String(e.reps),
						weight: e.weightKg ? String(e.weightKg) : '',
						rest: e.restSeconds === null ? '' : String(e.restSeconds)
					}))
				: [blankRow()]
		)
	);
	let error = $state('');

	// the routines the plan can be linked to: finishing a workout then ticks that routine off
	const routines = createQuery(() => ({
		queryKey: ['lifestyle', 'routine', 'overview', today()],
		queryFn: () => routineApi.overview(today())
	}));

	function move(index: number, by: -1 | 1) {
		const target = index + by;
		if (target < 0 || target >= rows.length) return;
		const copy = [...rows];
		[copy[index], copy[target]] = [copy[target], copy[index]];
		rows = copy;
	}

	function build(): PlanInput | string {
		const exercises: PlanInput['exercises'] = [];
		for (const row of rows) {
			if (!row.name.trim()) continue;
			const sets = Math.round(Number(row.sets));
			const reps = Math.round(Number(row.reps));
			const weight = row.weight.trim() === '' ? null : Number(row.weight.replace(',', '.'));
			const rest = row.rest.trim() === '' ? null : Math.round(Number(row.rest));
			if (!(sets >= 1 && sets <= 20)) return `"${row.name}": sets must be 1 to 20`;
			if (!(reps >= 1 && reps <= 200)) return `"${row.name}": reps must be 1 to 200`;
			if (weight !== null && !(weight >= 0 && weight <= 1000)) return `"${row.name}": weight must be 0 to 1000 kg`;
			if (rest !== null && !(rest >= 0 && rest <= 900)) return `"${row.name}": rest must be 0 to 900 seconds`;
			exercises.push({ name: row.name.trim(), sets, reps, weightKg: weight, restSeconds: rest, note: null });
		}
		if (!name.trim()) return 'Give the plan a name';
		return { name: name.trim(), note: note.trim() || null, routineId: routineId ? Number(routineId) : null, exercises };
	}

	const save = createMutation(() => ({
		mutationFn: async (body: PlanInput): Promise<void> => {
			if (plan) await trainingApi.updatePlan(plan.id, body);
			else await trainingApi.createPlan(body);
		},
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: trainingKey });
			onDone();
		},
		onError: (e: Error) => (error = e.message)
	}));

	function submit(event: SubmitEvent) {
		event.preventDefault();
		const built = build();
		if (typeof built === 'string') {
			error = built;
			return;
		}
		error = '';
		save.mutate(built);
	}

	const field = 'rounded bg-slate-800 px-2 py-1.5 text-slate-100';
</script>

<form onsubmit={submit} class="space-y-3 rounded-lg border border-slate-700 p-3">
	<h3 class="font-semibold">{plan ? 'Edit plan' : 'New plan'}</h3>

	<label class="block text-sm text-slate-300">
		Name
		<input bind:value={name} maxlength="200" placeholder="Push day" class="mt-1 block w-full {field}" />
	</label>

	<div class="space-y-2">
		<p class="text-sm text-slate-300">Exercises</p>
		{#each rows as row, i (row.key)}
			<div class="rounded-md border border-slate-800 p-2">
				<div class="flex items-center gap-2">
					<input
						bind:value={row.name}
						list="plan-exercises"
						maxlength="200"
						placeholder="Exercise, e.g. Bench Press"
						aria-label="Exercise {i + 1}"
						class="min-w-0 flex-1 {field}"
					/>
					<button type="button" onclick={() => move(i, -1)} disabled={i === 0} aria-label="Move up" class="px-1 text-slate-400 hover:text-slate-200 disabled:opacity-30">↑</button>
					<button type="button" onclick={() => move(i, 1)} disabled={i === rows.length - 1} aria-label="Move down" class="px-1 text-slate-400 hover:text-slate-200 disabled:opacity-30">↓</button>
					<button type="button" onclick={() => (rows = rows.filter((r) => r.key !== row.key))} aria-label="Remove exercise" class="px-1 text-slate-500 hover:text-red-400">✕</button>
				</div>
				<div class="mt-2 flex flex-wrap items-end gap-2 text-xs text-slate-400">
					<label>Sets<input bind:value={row.sets} inputmode="numeric" class="mt-0.5 block w-14 {field}" /></label>
					<label>Reps<input bind:value={row.reps} inputmode="numeric" class="mt-0.5 block w-14 {field}" /></label>
					<label>kg<input bind:value={row.weight} inputmode="decimal" placeholder="body" class="mt-0.5 block w-20 {field}" /></label>
					<label>Rest (s)<input bind:value={row.rest} inputmode="numeric" placeholder="90" class="mt-0.5 block w-16 {field}" /></label>
				</div>
			</div>
		{/each}
		<datalist id="plan-exercises">
			{#each knownExercises as exercise (exercise)}<option value={exercise}></option>{/each}
		</datalist>
		<button type="button" onclick={() => (rows = [...rows, blankRow()])} class="text-sm text-brand-400 hover:text-brand-300">+ Add exercise</button>
	</div>

	<label class="block text-sm text-slate-300">
		Counts for routine
		<select bind:value={routineId} class="mt-1 block w-full {field}">
			<option value="">None</option>
			{#each routines.data?.routines ?? [] as routine (routine.id)}
				<option value={String(routine.id)}>{routine.name}</option>
			{/each}
		</select>
		<span class="mt-1 block text-xs text-slate-500">
			Finishing a workout from this plan ticks that routine off for the day, with the time you trained.
		</span>
	</label>

	<label class="block text-sm text-slate-300">
		Note (optional)
		<input bind:value={note} maxlength="500" class="mt-1 block w-full {field}" />
	</label>

	{#if error}<p class="text-sm text-red-400">{error}</p>{/if}

	<div class="flex items-center gap-3">
		<button type="submit" disabled={save.isPending} class="rounded-md bg-brand-500 px-4 py-2 text-sm font-semibold text-slate-950 hover:bg-brand-400 disabled:opacity-60">
			{plan ? 'Save plan' : 'Create plan'}
		</button>
		<button type="button" onclick={onDone} class="text-sm text-slate-400 hover:text-slate-200">Cancel</button>
	</div>
</form>
