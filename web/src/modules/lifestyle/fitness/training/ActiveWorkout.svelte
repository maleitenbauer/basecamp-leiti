<script lang="ts">
	import { createMutation, useQueryClient } from '@tanstack/svelte-query';
	import { clock } from './format';
	import { showWorkout, trainingApi, trainingKey } from './api';
	import ExerciseCard from './ExerciseCard.svelte';
	import { progressLine, startMessage } from './motivation';
	import { useNow } from './now.svelte';
	import type { FinishResult, TrainingStats, Workout } from './types';

	/** The running workout: timer, motivation, every exercise with its sets, and the buttons to finish or throw it away. */
	let {
		workout,
		stats,
		knownExercises,
		onFinished
	}: { workout: Workout; stats: TrainingStats; knownExercises: string[]; onFinished: (result: FinishResult) => void } = $props();

	const DEFAULT_REST_SECONDS = 90;

	const queryClient = useQueryClient();
	const now = useNow();

	const elapsed = $derived(Math.max(0, (now.value - Date.parse(workout.startedAt)) / 1000));
	const autoFinishTime = $derived(
		new Date(workout.autoFinishAt).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
	);

	// ---- motivation: the line you got when you pressed Start, then how far along you are ----

	const message = $derived(
		startMessage({
			date: workout.day,
			hour: new Date(workout.startedAt).getHours(),
			planName: workout.planId === null ? null : workout.name,
			workoutsThisWeek: stats.workoutsThisWeek,
			weeklyTarget: stats.weeklyTarget,
			daysSinceLast: stats.daysSinceLast,
			totalWorkouts: stats.totalWorkouts
		})
	);
	const plannedSets = $derived(workout.exercises.reduce((sum, e) => sum + (e.targetSets ?? 0), 0));
	const doneSets = $derived(workout.exercises.reduce((sum, e) => sum + Math.min(e.sets.length, e.targetSets ?? 0), 0));
	const loggedSets = $derived(workout.exercises.reduce((sum, e) => sum + e.sets.length, 0));
	const encouragement = $derived(progressLine(doneSets, plannedSets, workout.day));

	// ---- rest timer between sets ----

	let rest = $state<{ until: number; total: number; fired: boolean } | null>(null);
	const restLeft = $derived(rest ? Math.ceil((rest.until - now.value) / 1000) : 0);

	function startRest(seconds: number | null) {
		const total = seconds ?? DEFAULT_REST_SECONDS;
		rest = total > 0 ? { until: Date.now() + total * 1000, total, fired: false } : null;
	}

	$effect(() => {
		if (rest && !rest.fired && restLeft <= 0) {
			rest.fired = true;
			navigator.vibrate?.([200, 100, 200]);
		}
	});

	// ---- keep the screen on while training; the browser lets go of it whenever the page is hidden ----

	$effect(() => {
		let lock: WakeLockSentinel | null = null;
		const acquire = async () => {
			try {
				lock = (await navigator.wakeLock?.request('screen')) ?? null;
			} catch {
				// not supported, or the battery saver said no: the workout works the same
			}
		};
		const onVisible = () => {
			if (document.visibilityState === 'visible') acquire();
		};
		acquire();
		document.addEventListener('visibilitychange', onVisible);
		return () => {
			document.removeEventListener('visibilitychange', onVisible);
			lock?.release().catch(() => {});
		};
	});

	// ---- adding an exercise, finishing, discarding ----

	let newExercise = $state('');
	let error = $state('');

	const refreshAround = () => {
		queryClient.invalidateQueries({ queryKey: trainingKey });
		queryClient.invalidateQueries({ queryKey: ['lifestyle', 'routine'] });
		queryClient.invalidateQueries({ queryKey: ['report'] });
	};

	const add = createMutation(() => ({
		mutationFn: (name: string) => trainingApi.addExercise(workout.id, name),
		onSuccess: (updated: Workout) => {
			newExercise = '';
			error = '';
			showWorkout(queryClient, updated);
		},
		onError: (e: Error) => (error = e.message)
	}));
	const finish = createMutation(() => ({
		mutationFn: () => trainingApi.finish(workout.id),
		onSuccess: (result: FinishResult) => {
			refreshAround();
			onFinished(result);
		},
		onError: (e: Error) => (error = e.message)
	}));
	const discard = createMutation(() => ({
		mutationFn: () => trainingApi.discard(workout.id),
		onSuccess: () => {
			showWorkout(queryClient, null);
			refreshAround();
		},
		onError: (e: Error) => (error = e.message)
	}));

	function submitExercise(event: SubmitEvent) {
		event.preventDefault();
		const name = newExercise.trim();
		if (name) add.mutate(name);
	}
</script>

<div class="space-y-4">
	<header class="rounded-lg border border-brand-500/40 bg-brand-500/10 p-4">
		<div class="flex items-start justify-between gap-3">
			<div class="min-w-0">
				<p class="text-xs tracking-wide text-brand-400 uppercase">{workout.name}</p>
				<h2 class="mt-0.5 text-lg font-semibold">{message.headline}</h2>
			</div>
			<p class="shrink-0 text-3xl font-semibold tabular-nums" aria-label="Time so far">{clock(elapsed)}</p>
		</div>
		<p class="mt-1 text-sm text-slate-300">{message.line}</p>
		<p class="mt-2 text-sm text-slate-400">{encouragement}</p>
		{#if plannedSets > 0}
			<div
				class="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-800"
				role="progressbar"
				aria-valuenow={doneSets}
				aria-valuemin="0"
				aria-valuemax={plannedSets}
			>
				<div class="h-full rounded-full bg-emerald-400" style="width: {Math.round((doneSets / plannedSets) * 100)}%"></div>
			</div>
		{/if}
		<p class="mt-2 text-xs text-slate-500">If you forget to finish, it is saved automatically at {autoFinishTime}.</p>
	</header>

	{#each workout.exercises as exercise (exercise.id)}
		<ExerciseCard {workout} {exercise} onLogged={startRest} />
	{/each}

	<form onsubmit={submitExercise} class="flex gap-2">
		<input
			bind:value={newExercise}
			list="known-exercises"
			maxlength="200"
			placeholder="Add an exercise"
			aria-label="Exercise to add"
			class="min-w-0 flex-1 rounded bg-slate-800 px-3 py-2 text-slate-100"
		/>
		<datalist id="known-exercises">
			{#each knownExercises as name (name)}<option value={name}></option>{/each}
		</datalist>
		<button type="submit" disabled={add.isPending || !newExercise.trim()} class="rounded-md bg-slate-800 px-3 py-2 text-sm hover:bg-slate-700 disabled:opacity-50">
			+ Add
		</button>
	</form>

	{#if error}<p class="text-sm text-red-400">{error}</p>{/if}

	<div class="flex flex-wrap items-center gap-3">
		<button
			onclick={() => finish.mutate()}
			disabled={finish.isPending}
			class="rounded-md bg-emerald-500 px-5 py-2.5 font-semibold text-slate-950 hover:bg-emerald-400 disabled:opacity-60"
		>
			Finish workout
		</button>
		<button
			onclick={() =>
				confirm(loggedSets > 0 ? `Throw away this workout and its ${loggedSets} logged sets?` : 'Throw away this workout?') &&
				discard.mutate()}
			disabled={discard.isPending}
			class="text-sm text-slate-500 hover:text-red-400"
		>
			Discard
		</button>
	</div>

	{#if rest}
		<div class="sticky bottom-3 rounded-lg border border-slate-600 bg-slate-900/95 p-3 shadow-lg" role="timer" aria-live="off">
			<div class="flex items-center justify-between gap-3">
				{#if restLeft > 0}
					<p class="text-sm text-slate-300">Rest <span class="text-xl font-semibold tabular-nums text-slate-100">{clock(restLeft)}</span></p>
				{:else}
					<p class="text-sm font-semibold text-emerald-400">Rest is over. Ready for the next set.</p>
				{/if}
				<button onclick={() => (rest = null)} class="text-sm text-slate-400 hover:text-slate-200">
					{restLeft > 0 ? 'Skip' : 'Dismiss'}
				</button>
			</div>
			<div class="mt-2 h-1 overflow-hidden rounded-full bg-slate-800">
				<div
					class="h-full rounded-full {restLeft > 0 ? 'bg-brand-400' : 'bg-emerald-400'}"
					style="width: {restLeft > 0 ? Math.min(100, (restLeft / rest.total) * 100) : 100}%"
				></div>
			</div>
		</div>
	{/if}
</div>
