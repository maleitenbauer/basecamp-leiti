<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { prettyDate, shiftDate, today } from '$lib/dates';
	import { routineApi } from './api';
	import RoutineForm from './RoutineForm.svelte';
	import RoutineReminderForm from './RoutineReminderForm.svelte';
	import RoutineRow from './RoutineRow.svelte';

	const now = today();
	let date = $state(now);
	let adding = $state(false);

	// clearing the native date picker yields '' — fall back to today instead of querying an empty date
	$effect(() => {
		if (!date) date = now;
	});

	const overview = createQuery(() => ({
		queryKey: ['lifestyle', 'routine', 'overview', date],
		queryFn: () => routineApi.overview(date)
	}));

	// declared before the query below, which reads it as soon as it is created
	let showReminder = $state(false);

	const reminder = createQuery(() => ({
		queryKey: ['lifestyle', 'routine', 'reminder'],
		queryFn: () => routineApi.reminder(),
		enabled: showReminder
	}));

	const open = $derived(overview.data?.routines.filter((r) => r.state === 'OPEN') ?? []);
	const done = $derived(overview.data?.routines.filter((r) => r.state === 'DONE') ?? []);
	const notScheduled = $derived(overview.data?.routines.filter((r) => r.state === 'NOT_DUE') ?? []);
</script>

<main class="mx-auto max-w-2xl space-y-4 p-4 sm:p-6">
	<div class="flex flex-wrap items-center justify-between gap-2">
		<div>
			<h2 class="text-lg font-semibold">{date === now ? 'Today' : prettyDate(date)}</h2>
			{#if date === now}<p class="text-sm text-slate-400">{prettyDate(date)}</p>{/if}
		</div>
		<div class="flex items-center gap-1 text-sm">
			<button
				onclick={() => (date = shiftDate(date, -1))}
				class="rounded border border-slate-700 px-2 py-1 hover:bg-slate-800"
				title="Previous day"
			>
				◀
			</button>
			<input type="date" bind:value={date} class="rounded bg-slate-800 px-2 py-1 text-slate-200" aria-label="Day" />
			<button
				onclick={() => (date = shiftDate(date, 1))}
				class="rounded border border-slate-700 px-2 py-1 hover:bg-slate-800"
				title="Next day"
			>
				▶
			</button>
			{#if date !== now}
				<button onclick={() => (date = now)} class="ml-1 text-brand-400 hover:text-brand-300">Today</button>
			{/if}
		</div>
	</div>

	{#if overview.isPending}
		<p class="text-slate-400">Loading…</p>
	{:else if overview.isError}
		<p class="text-red-400">Could not load your routines: {overview.error.message}</p>
	{:else}
		{@const total = overview.data.routines.length}
		{#if total > 0}
			<p class="text-sm {overview.data.openCount === 0 ? 'text-emerald-400' : 'text-slate-300'}">
				{overview.data.openCount === 0
					? 'Everything is done.'
					: `${overview.data.openCount} of ${total} still open.`}
			</p>
		{/if}

		{#if total === 0}
			<p class="rounded-lg border border-dashed border-slate-700 p-6 text-sm text-slate-400">
				No routines yet. Add one below — something you do every day, or a few times a week.
			</p>
		{/if}

		{#if open.length > 0}
			<section>
				<h3 class="mb-2 text-sm font-semibold text-slate-300">Still open</h3>
				<ul class="divide-y divide-slate-800 rounded-lg border border-slate-700">
					{#each open as routine (routine.id)}
						<RoutineRow {routine} {date} weekStart={overview.data.weekStart} />
					{/each}
				</ul>
			</section>
		{/if}

		{#if done.length > 0}
			<section>
				<h3 class="mb-2 text-sm font-semibold text-slate-300">Done</h3>
				<ul class="divide-y divide-slate-800 rounded-lg border border-slate-700">
					{#each done as routine (routine.id)}
						<RoutineRow {routine} {date} weekStart={overview.data.weekStart} />
					{/each}
				</ul>
			</section>
		{/if}

		{#if notScheduled.length > 0}
			<section>
				<h3 class="mb-2 text-sm font-semibold text-slate-400">Not scheduled {date === now ? 'today' : 'this day'}</h3>
				<ul class="divide-y divide-slate-800 rounded-lg border border-slate-800">
					{#each notScheduled as routine (routine.id)}
						<RoutineRow {routine} {date} weekStart={overview.data.weekStart} />
					{/each}
				</ul>
			</section>
		{/if}

		{#if adding}
			<RoutineForm onDone={() => (adding = false)} />
		{:else}
			<button
				onclick={() => (adding = true)}
				class="rounded-md bg-slate-800 px-3 py-2 text-sm hover:bg-slate-700"
			>
				+ Add a routine
			</button>
		{/if}
	{/if}

	<details
		class="rounded-lg border border-slate-800 p-3"
		ontoggle={(e) => (showReminder = e.currentTarget.open)}
	>
		<summary class="cursor-pointer text-sm text-slate-400">Reminder</summary>
		{#if reminder.isPending}
			<p class="mt-2 text-sm text-slate-400">Loading…</p>
		{:else if reminder.isError}
			<p class="mt-2 text-sm text-red-400">Could not load reminder settings: {reminder.error.message}</p>
		{:else}
			{#key reminder.data}
				<RoutineReminderForm settings={reminder.data} />
			{/key}
		{/if}
	</details>
</main>
