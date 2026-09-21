<script lang="ts">
	import { createMutation, createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { shiftDate, today } from '$lib/dates';
	import { todosApi } from './api';
	import ReminderForm from './ReminderForm.svelte';
	import TodoItem from './TodoItem.svelte';
	import { dueGroup, type DueGroup } from './types';

	const queryClient = useQueryClient();
	const now = today();

	const list = createQuery(() => ({
		queryKey: ['logbook', 'todos'],
		queryFn: () => todosApi.list()
	}));

	// declared before the query below, which reads it as soon as it is created
	let showReminders = $state(false);

	const reminders = createQuery(() => ({
		queryKey: ['logbook', 'todos', 'reminders'],
		queryFn: () => todosApi.reminders(),
		enabled: showReminders
	}));

	const sections: { key: DueGroup; label: string; tone: string }[] = [
		{ key: 'overdue', label: 'Overdue', tone: 'text-red-400' },
		{ key: 'today', label: 'Today', tone: 'text-brand-400' },
		{ key: 'upcoming', label: 'Upcoming', tone: 'text-slate-400' },
		{ key: 'none', label: 'No deadline', tone: 'text-slate-400' }
	];

	const groups = $derived(
		sections
			.map((s) => ({ ...s, items: (list.data?.open ?? []).filter((t) => dueGroup(t.dueDate, now) === s.key) }))
			.filter((g) => g.items.length > 0)
	);
	const overdueCount = $derived((list.data?.open ?? []).filter((t) => dueGroup(t.dueDate, now) === 'overdue').length);

	// ---- add form ----
	let title = $state('');
	let dueDate = $state('');
	let note = $state('');
	let noteOpen = $state(false);

	const create = createMutation(() => ({
		mutationFn: () =>
			todosApi.create({
				title: title.trim(),
				dueDate: dueDate || undefined,
				note: note.trim() || undefined
			}),
		onSuccess: () => {
			title = '';
			dueDate = '';
			note = '';
			noteOpen = false;
			queryClient.invalidateQueries({ queryKey: ['logbook', 'todos'] });
		}
	}));
</script>

<main class="mx-auto max-w-4xl p-4 sm:p-6">
	<div class="flex flex-wrap items-baseline justify-between gap-2">
		<h1 class="text-2xl font-semibold">Todos</h1>
		{#if list.data}
			<p class="text-sm text-slate-400">
				{list.data.open.length} open{#if overdueCount > 0}
					· <span class="text-red-400">{overdueCount} overdue</span>{/if}
			</p>
		{/if}
	</div>

	<form
		onsubmit={(e) => {
			e.preventDefault();
			if (title.trim()) create.mutate();
		}}
		class="mt-4 space-y-2 rounded-lg border border-slate-700 p-3"
	>
		<div class="flex flex-wrap gap-2">
			<input
				bind:value={title}
				placeholder="What needs doing?"
				maxlength="300"
				required
				aria-label="New todo"
				class="min-w-48 flex-1 rounded-md bg-slate-800 px-3 py-2 text-sm"
			/>
			<input
				type="date"
				bind:value={dueDate}
				aria-label="Deadline"
				class="rounded-md bg-slate-800 px-2 py-2 text-sm"
			/>
			<button
				type="submit"
				disabled={create.isPending}
				class="rounded-md bg-brand-500 px-4 py-2 text-sm font-medium text-slate-950 hover:bg-brand-400 disabled:opacity-50"
			>
				Add
			</button>
		</div>
		<div class="flex flex-wrap items-center gap-3 text-xs text-slate-400">
			<span>Deadline:</span>
			<button type="button" onclick={() => (dueDate = now)} class="hover:text-white">Today</button>
			<button type="button" onclick={() => (dueDate = shiftDate(now, 1))} class="hover:text-white">Tomorrow</button>
			<button type="button" onclick={() => (dueDate = shiftDate(now, 7))} class="hover:text-white">In a week</button>
			{#if dueDate}
				<button type="button" onclick={() => (dueDate = '')} class="hover:text-white">Clear</button>
			{/if}
			<span class="ml-auto"></span>
			<button type="button" onclick={() => (noteOpen = !noteOpen)} class="hover:text-white">
				{noteOpen ? '− Note' : '+ Note'}
			</button>
		</div>
		{#if noteOpen}
			<textarea
				bind:value={note}
				rows="3"
				maxlength="4000"
				placeholder="Note (optional)"
				aria-label="Note"
				class="w-full rounded-md bg-slate-800 px-3 py-2 text-sm"
			></textarea>
		{/if}
		{#if create.isError}<p class="text-sm text-red-400">{create.error.message}</p>{/if}
	</form>

	<div class="mt-4 space-y-5">
		{#if list.isPending}
			<p class="text-slate-400">Loading…</p>
		{:else if list.isError}
			<p class="text-red-400">Could not load todos: {list.error.message}</p>
		{:else}
			{#if groups.length === 0}
				<p class="text-slate-500">Nothing to do. Add your first todo above.</p>
			{/if}

			{#each groups as g (g.key)}
				<section>
					<h2 class="mb-2 text-sm font-medium tracking-wide uppercase {g.tone}">
						{g.label} <span class="text-slate-600">· {g.items.length}</span>
					</h2>
					<ul class="space-y-2">
						{#each g.items as todo (todo.id)}
							<TodoItem {todo} {now} />
						{/each}
					</ul>
				</section>
			{/each}

			<details class="rounded-lg border border-slate-800 p-3">
				<summary class="cursor-pointer text-sm text-slate-400">
					Done ({list.data.doneTotal})
				</summary>
				{#if list.data.done.length === 0}
					<p class="mt-2 text-sm text-slate-500">Finished todos show up here.</p>
				{:else}
					<ul class="mt-3 space-y-2">
						{#each list.data.done as todo (todo.id)}
							<TodoItem {todo} {now} />
						{/each}
					</ul>
					{#if list.data.doneTotal > list.data.done.length}
						<p class="mt-2 text-xs text-slate-500">Showing the latest {list.data.done.length}.</p>
					{/if}
				{/if}
			</details>
		{/if}

		<details
			class="rounded-lg border border-slate-800 p-3"
			ontoggle={(e) => (showReminders = e.currentTarget.open)}
		>
			<summary class="cursor-pointer text-sm text-slate-400">Reminders</summary>
			{#if reminders.isPending}
				<p class="mt-2 text-sm text-slate-400">Loading…</p>
			{:else if reminders.isError}
				<p class="mt-2 text-sm text-red-400">Could not load reminder settings: {reminders.error.message}</p>
			{:else}
				{#key reminders.data}
					<ReminderForm settings={reminders.data} />
				{/key}
			{/if}
		</details>
	</div>
</main>
