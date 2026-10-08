<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { today } from '$lib/dates';
	import { nutritionApi } from './api';

	const date = today();

	// the same query as the Nutrition page
	const day = createQuery(() => ({
		queryKey: ['lifestyle', 'fitness', 'nutrition', 'day', date],
		queryFn: () => nutritionApi.day(date)
	}));

	const over = $derived((day.data?.remainingKcal ?? 0) < 0);
	const percent = $derived(
		day.data?.goalKcal ? Math.min(100, Math.round((day.data.consumedKcal / day.data.goalKcal) * 100)) : 0
	);
</script>

{#if day.isPending}
	<p class="text-sm text-slate-400">Loading…</p>
{:else if day.isError}
	<p class="text-sm text-red-400">{day.error.message}</p>
{:else if day.data.goalKcal === null}
	<p class="text-sm text-slate-300">{day.data.consumedKcal} kcal eaten today</p>
	<a href="/lifestyle/fitness" class="mt-1 inline-block text-sm text-brand-400 hover:text-brand-300">Set a daily limit</a>
{:else}
	<p class="text-3xl font-semibold tabular-nums {over ? 'text-red-400' : 'text-emerald-400'}">
		{Math.abs(day.data.remainingKcal ?? 0)}
		<span class="text-sm font-normal text-slate-400">kcal {over ? 'over' : 'left'}</span>
	</p>
	<div class="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-800" role="progressbar" aria-valuenow={percent} aria-valuemin="0" aria-valuemax="100">
		<div class="h-full rounded-full {over ? 'bg-red-400' : 'bg-emerald-400'}" style="width: {percent}%"></div>
	</div>
	<p class="mt-1.5 text-xs text-slate-500">
		{day.data.consumedKcal} of {day.data.goalKcal} eaten
		{#if day.data.entries.length === 0}· <a href="/lifestyle/fitness" class="text-brand-400 hover:text-brand-300">log a meal</a>{/if}
	</p>
{/if}
