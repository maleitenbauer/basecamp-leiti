<script lang="ts">
	import { createMutation, createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { prettyDate, shiftDate, today, weekday } from '$lib/dates';
	import { cs2Api } from './api';
	import { ROUTINE_CATEGORIES, type RoutineCategory, type RoutineItem } from './types';

	const queryClient = useQueryClient();
	const refresh = () => queryClient.invalidateQueries({ queryKey: ['gaming', 'cs2'] });

	let date = $state(today());

	const routine = createQuery(() => ({
		queryKey: ['gaming', 'cs2', 'routine', date],
		queryFn: () => cs2Api.routine(date),
		enabled: !!date // the date input can be cleared
	}));

	const activeItems = $derived((routine.data?.items ?? []).filter((i) => i.active));
	const doneCount = $derived(activeItems.filter((i) => i.done).length);
	const maxHistory = $derived(Math.max(1, ...(routine.data?.history ?? []).map((h) => h.doneCount)));

	const groups = $derived(
		ROUTINE_CATEGORIES.map((c) => ({
			...c,
			items: activeItems.filter((i) => i.category === c.value)
		})).filter((g) => g.items.length > 0)
	);

	const setDone = createMutation(() => ({
		mutationFn: (v: { id: number; done: boolean; minutes?: number }) =>
			cs2Api.setDone(v.id, date, { done: v.done, minutes: v.minutes }),
		onSuccess: refresh
	}));

	function toggle(item: RoutineItem, done: boolean) {
		setDone.mutate({ id: item.id, done, minutes: done ? (item.targetMinutes ?? undefined) : undefined });
	}

	function changeMinutes(item: RoutineItem, value: string) {
		const minutes = Number(value);
		if (minutes >= 1 && minutes <= 600) setDone.mutate({ id: item.id, done: true, minutes });
	}

	// --- editing the routine ---
	let newTitle = $state('');
	let newCategory = $state<RoutineCategory>('PRACTICE');
	let newMinutes = $state<number | null>(null);

	const createItem = createMutation(() => ({
		mutationFn: () =>
			cs2Api.createItem({
				title: newTitle.trim(),
				category: newCategory,
				targetMinutes: newMinutes ?? undefined
			}),
		onSuccess: () => {
			newTitle = '';
			newMinutes = null;
			refresh();
		}
	}));

	const updateItem = createMutation(() => ({
		mutationFn: (v: { id: number; active: boolean }) => cs2Api.updateItem(v.id, { active: v.active }),
		onSuccess: refresh
	}));

	const deleteItem = createMutation(() => ({
		mutationFn: (id: number) => cs2Api.deleteItem(id),
		onSuccess: refresh
	}));

	function confirmDelete(item: RoutineItem) {
		if (confirm(`Delete "${item.title}" and its history?`)) deleteItem.mutate(item.id);
	}
</script>

<div class="space-y-4">
	<div class="flex flex-wrap items-center gap-2">
		<button
			onclick={() => (date = shiftDate(date, -1))}
			class="rounded-md bg-slate-800 px-3 py-1.5 hover:bg-slate-700"
			aria-label="Previous day">←</button
		>
		<input
			type="date"
			bind:value={date}
			max={today()}
			class="rounded-md bg-slate-800 px-2 py-1.5 text-sm"
			aria-label="Day"
		/>
		<button
			onclick={() => (date = shiftDate(date, 1))}
			disabled={date >= today()}
			class="rounded-md bg-slate-800 px-3 py-1.5 hover:bg-slate-700 disabled:opacity-40"
			aria-label="Next day">→</button
		>
		{#if date !== today()}
			<button onclick={() => (date = today())} class="text-sm text-emerald-400 hover:text-emerald-300">
				Today
			</button>
		{/if}
		<span class="ml-auto text-sm text-slate-400">{prettyDate(date)}</span>
	</div>

	{#if routine.isPending}
		<p class="text-slate-400">Loading…</p>
	{:else if routine.isError}
		<p class="text-red-400">Could not load the routine: {routine.error.message}</p>
	{:else}
		<section class="flex flex-wrap items-center gap-4 rounded-lg border border-slate-700 p-3">
			<div>
				<div class="text-2xl font-semibold">🔥 {routine.data.streak}</div>
				<div class="text-xs text-slate-400">day streak</div>
			</div>
			<div>
				<div class="text-2xl font-semibold">{doneCount}/{activeItems.length}</div>
				<div class="text-xs text-slate-400">done this day</div>
			</div>
			<div class="ml-auto flex items-end gap-1" role="img" aria-label="Items done over the last 14 days">
				{#each routine.data.history as h (h.date)}
					<div class="flex flex-col items-center gap-1" title="{h.date}: {h.doneCount} done">
						<div
							class="w-3 rounded-sm {h.date === date ? 'bg-emerald-400' : 'bg-emerald-700'}"
							style="height: {h.doneCount === 0 ? 3 : 6 + (h.doneCount / maxHistory) * 26}px; opacity: {h.doneCount === 0
								? 0.25
								: 1}"
						></div>
						<span class="text-[9px] text-slate-500">{weekday(h.date).slice(0, 2)}</span>
					</div>
				{/each}
			</div>
		</section>

		{#each groups as group (group.value)}
			<section>
				<h2 class="mb-2 text-sm font-medium tracking-wide text-slate-400 uppercase">{group.label}</h2>
				<ul class="space-y-2">
					{#each group.items as item (item.id)}
						<li class="rounded-lg border border-slate-700 bg-slate-800/40 p-3">
							<div class="flex items-start gap-3">
								<input
									type="checkbox"
									checked={item.done}
									onchange={(e) => toggle(item, e.currentTarget.checked)}
									class="mt-1 h-5 w-5 accent-emerald-500"
									aria-label="Done: {item.title}"
								/>
								<div class="min-w-0 flex-1">
									<div class="font-medium {item.done ? 'text-slate-400 line-through' : ''}">
										{item.title}
										{#if item.targetMinutes}
											<span class="text-xs font-normal text-slate-500">· {item.targetMinutes} min</span>
										{/if}
									</div>
									{#if item.description}
										<p class="mt-0.5 text-sm text-slate-400">{item.description}</p>
									{/if}
								</div>
								{#if item.done}
									<label class="flex items-center gap-1 text-xs text-slate-400">
										<input
											type="number"
											min="1"
											max="600"
											value={item.minutes ?? ''}
											onchange={(e) => changeMinutes(item, e.currentTarget.value)}
											class="w-16 rounded-md bg-slate-800 px-2 py-1 text-sm text-slate-100"
											aria-label="Minutes for {item.title}"
										/>
										min
									</label>
								{/if}
							</div>
						</li>
					{/each}
				</ul>
			</section>
		{/each}
		{#if activeItems.length === 0}
			<p class="text-slate-500">No active routine items. Add some below.</p>
		{/if}
		{#if setDone.isError}<p class="text-sm text-red-400">{setDone.error.message}</p>{/if}

		<details class="rounded-lg border border-slate-800 p-3">
			<summary class="cursor-pointer text-sm text-slate-400">Edit routine</summary>
			<form
				onsubmit={(e) => {
					e.preventDefault();
					if (newTitle.trim()) createItem.mutate();
				}}
				class="mt-3 flex flex-wrap gap-2"
			>
				<input
					bind:value={newTitle}
					placeholder="New routine item…"
					maxlength="200"
					required
					class="min-w-40 flex-1 rounded-md bg-slate-800 px-3 py-2 text-sm"
				/>
				<select bind:value={newCategory} class="rounded-md bg-slate-800 px-2 py-2 text-sm">
					{#each ROUTINE_CATEGORIES as c (c.value)}
						<option value={c.value}>{c.label}</option>
					{/each}
				</select>
				<input
					type="number"
					min="1"
					max="600"
					bind:value={newMinutes}
					placeholder="Min"
					class="w-20 rounded-md bg-slate-800 px-2 py-2 text-sm"
				/>
				<button
					type="submit"
					disabled={createItem.isPending}
					class="rounded-md bg-emerald-600 px-4 py-2 text-sm font-medium hover:bg-emerald-500 disabled:opacity-50"
				>
					Add
				</button>
			</form>
			{#if createItem.isError}<p class="mt-2 text-sm text-red-400">{createItem.error.message}</p>{/if}

			<ul class="mt-3 divide-y divide-slate-800 text-sm">
				{#each routine.data.items as item (item.id)}
					<li class="flex items-center gap-3 py-2">
						<span class="min-w-0 flex-1 truncate {item.active ? '' : 'text-slate-500 line-through'}">
							{item.title}
						</span>
						<button
							onclick={() => updateItem.mutate({ id: item.id, active: !item.active })}
							class="text-slate-400 hover:text-white"
						>
							{item.active ? 'Hide' : 'Show'}
						</button>
						<button onclick={() => confirmDelete(item)} class="text-red-400 hover:text-red-300">
							Delete
						</button>
					</li>
				{/each}
			</ul>
		</details>
	{/if}
</div>
