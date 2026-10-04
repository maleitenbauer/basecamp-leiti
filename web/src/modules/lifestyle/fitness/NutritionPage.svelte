<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import AddMealForm from './AddMealForm.svelte';
	import CalorieSummary from './CalorieSummary.svelte';
	import MealEntryRow from './MealEntryRow.svelte';
	import { nutritionApi } from './api';
	import { localDateString, shiftDate } from './types';

	const today = localDateString();
	let date = $state(today);

	// clearing the native date picker yields '' — fall back to today instead of querying an empty date
	$effect(() => {
		if (!date) date = today;
	});

	const day = createQuery(() => ({
		queryKey: ['lifestyle', 'fitness', 'nutrition', 'day', date],
		queryFn: () => nutritionApi.day(date)
	}));

	const foods = createQuery(() => ({
		queryKey: ['lifestyle', 'fitness', 'nutrition', 'foods'],
		queryFn: () => nutritionApi.foods()
	}));

	const label = $derived.by(() => {
		if (date === today) return 'Today';
		if (date === shiftDate(today, -1)) return 'Yesterday';
		const [y, m, d] = date.split('-').map(Number);
		return new Date(y, m - 1, d).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
	});
</script>

<main class="mx-auto max-w-2xl space-y-4 p-4 sm:p-6">
	<div class="flex items-center justify-between gap-2">
		<h2 class="text-lg font-semibold">Nutrition</h2>
		<div class="flex items-center gap-1 text-sm">
			<button
				onclick={() => (date = shiftDate(date, -1))}
				class="rounded border border-slate-700 px-2 py-1 hover:bg-slate-800"
				title="Previous day"
			>
				◀
			</button>
			<input
				type="date"
				bind:value={date}
				class="rounded bg-slate-800 px-2 py-1 text-slate-200"
				aria-label="Day"
			/>
			<button
				onclick={() => (date = shiftDate(date, 1))}
				class="rounded border border-slate-700 px-2 py-1 hover:bg-slate-800"
				title="Next day"
			>
				▶
			</button>
			{#if date !== today}
				<button onclick={() => (date = today)} class="ml-1 text-brand-400 hover:text-brand-300">Today</button>
			{/if}
		</div>
	</div>
	<p class="-mt-2 text-sm text-slate-400">{label}</p>

	{#if day.isPending}
		<p class="text-slate-400">Loading…</p>
	{:else if day.isError}
		<p class="text-red-400">Could not load this day: {day.error.message}</p>
	{:else}
		<CalorieSummary day={day.data} />

		<AddMealForm {date} foods={foods.data ?? []} />

		<section>
			<h3 class="mb-2 text-sm font-semibold text-slate-300">Meals</h3>
			{#if day.data.entries.length === 0}
				<p class="text-sm text-slate-500">Nothing logged for this day yet.</p>
			{:else}
				<ul class="divide-y divide-slate-800 rounded-lg border border-slate-700">
					{#each day.data.entries as entry (entry.id)}
						<MealEntryRow {entry} />
					{/each}
				</ul>
			{/if}
		</section>
	{/if}
</main>
