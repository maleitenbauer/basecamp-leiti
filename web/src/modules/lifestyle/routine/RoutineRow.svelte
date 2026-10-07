<script lang="ts">
	import { createMutation, useQueryClient } from '@tanstack/svelte-query';
	import { shiftDate, weekday } from '$lib/dates';
	import { routineApi } from './api';
	import RoutineForm from './RoutineForm.svelte';
	import type { Routine } from './types';

	let { routine, date, weekStart }: { routine: Routine; date: string; weekStart: string } = $props();

	const queryClient = useQueryClient();
	const refresh = () => queryClient.invalidateQueries({ queryKey: ['lifestyle', 'routine'] });

	let editing = $state(false);
	let editingMinutes = $state(false);
	let minutesDraft = $state('');

	const toggle = createMutation(() => ({
		mutationFn: () => (routine.doneToday ? routineApi.undo(routine.id, date) : routineApi.markDone(routine.id, date)),
		onSuccess: refresh
	}));
	const logMinutes = createMutation(() => ({
		mutationFn: (minutes: number) => routineApi.markDone(routine.id, date, minutes),
		onSuccess: () => {
			editingMinutes = false;
			refresh();
		}
	}));
	const remove = createMutation(() => ({ mutationFn: () => routineApi.remove(routine.id), onSuccess: refresh }));

	const flexible = $derived(routine.scheduleType === 'TIMES_PER_WEEK');
	const weekDays = $derived(Array.from({ length: 7 }, (_, i) => shiftDate(weekStart, i)));
	const weekTargetMet = $derived(flexible && !routine.open && !routine.doneToday);
	const notScheduled = $derived(routine.state === 'NOT_DUE');

	const short = (iso: string) =>
		new Date(`${iso}T12:00:00`).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });

	function startMinutes() {
		minutesDraft = String(routine.minutesToday ?? routine.targetMinutes ?? '');
		editingMinutes = true;
	}

	function submitMinutes() {
		const minutes = Math.round(Number(minutesDraft));
		if (minutes >= 1 && minutes <= 1440) logMinutes.mutate(minutes);
	}
</script>

{#if editing}
	<li class="p-3"><RoutineForm {routine} onDone={() => (editing = false)} /></li>
{:else}
	<li class="flex items-start gap-3 px-3 py-2.5 {notScheduled ? 'opacity-70' : ''}">
		<button
			onclick={() => toggle.mutate()}
			disabled={toggle.isPending}
			aria-pressed={routine.doneToday}
			aria-label={routine.doneToday ? `Mark "${routine.name}" not done` : `Mark "${routine.name}" done`}
			class="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-2 text-xs {routine.doneToday
				? 'border-emerald-400 bg-emerald-400 text-slate-950'
				: weekTargetMet
					? 'border-emerald-700 text-emerald-600'
					: 'border-slate-600 text-transparent hover:border-brand-400'}"
		>
			✓
		</button>

		<div class="min-w-0 flex-1">
			<p class="truncate text-sm {routine.doneToday ? 'text-slate-400 line-through' : ''}">{routine.name}</p>
			<p class="mt-0.5 flex flex-wrap items-center gap-x-2 text-xs text-slate-500">
				<span>{routine.summary}</span>
				{#if routine.targetMinutes}<span>· {routine.targetMinutes} min</span>{/if}
				{#if weekTargetMet}<span class="text-emerald-500">· done for this week</span>{/if}
				{#if routine.overdueSince}<span class="text-amber-400">· overdue since {short(routine.overdueSince)}</span>{/if}
				{#if notScheduled && routine.nextDue}<span>· next {short(routine.nextDue)}</span>{/if}
				{#if routine.note}<span class="truncate">· {routine.note}</span>{/if}
			</p>

			{#if flexible}
				<div class="mt-1.5 flex items-center gap-1" aria-label="This week">
					{#each weekDays as day (day)}
						<span
							class="flex h-5 w-5 items-center justify-center rounded-full text-[10px] {routine.doneDaysThisWeek.includes(day)
								? 'bg-emerald-400 text-slate-950'
								: day === date
									? 'border border-brand-400 text-slate-400'
									: 'bg-slate-800 text-slate-500'}"
							title={day}
						>
							{weekday(day).charAt(0)}
						</span>
					{/each}
					<span class="ml-1 text-xs text-slate-500">{routine.doneDaysThisWeek.length}/{routine.timesPerWeek}</span>
				</div>
			{/if}

			{#if routine.doneToday && routine.targetMinutes}
				{#if editingMinutes}
					<form
						class="mt-1.5 flex items-center gap-1 text-xs"
						onsubmit={(e) => {
							e.preventDefault();
							submitMinutes();
						}}
					>
						<input bind:value={minutesDraft} type="number" min="1" max="1440" class="w-16 rounded bg-slate-800 px-2 py-0.5" aria-label="Minutes spent" />
						<span class="text-slate-500">min</span>
						<button type="submit" disabled={logMinutes.isPending} class="text-brand-400 hover:text-brand-300">Save</button>
						<button type="button" onclick={() => (editingMinutes = false)} class="text-slate-500 hover:text-slate-300">Cancel</button>
					</form>
				{:else}
					<p class="mt-1 text-xs text-slate-500">
						Did {routine.minutesToday} min
						<button onclick={startMinutes} class="ml-1 text-slate-500 underline decoration-dotted hover:text-slate-200">change</button>
					</p>
				{/if}
			{/if}
		</div>

		<div class="flex shrink-0 gap-2 text-xs">
			<button onclick={() => (editing = true)} class="text-slate-500 hover:text-slate-200">Edit</button>
			<button
				onclick={() => confirm(`Delete "${routine.name}" and its history?`) && remove.mutate()}
				disabled={remove.isPending}
				class="text-slate-500 hover:text-red-400"
			>
				Delete
			</button>
		</div>
	</li>
{/if}
