<script lang="ts">
	import { createMutation, useQueryClient } from '@tanstack/svelte-query';
	import { nutritionApi } from './api';
	import type { NutritionDay } from './types';

	let { day }: { day: NutritionDay } = $props();

	const queryClient = useQueryClient();

	let editing = $state(false);
	let draft = $state('');

	const saveGoal = createMutation(() => ({
		mutationFn: (kcal: number) => nutritionApi.setGoal(kcal),
		onSuccess: () => {
			editing = false;
			queryClient.invalidateQueries({ queryKey: ['lifestyle', 'fitness', 'nutrition'] });
		}
	}));

	const over = $derived(day.remainingKcal !== null && day.remainingKcal < 0);
	const percent = $derived(
		day.goalKcal ? Math.min(100, Math.round((day.consumedKcal / day.goalKcal) * 100)) : 0
	);

	function startEdit() {
		draft = String(day.goalKcal ?? 2000);
		editing = true;
	}

	function submit() {
		const kcal = Number(draft);
		if (Number.isInteger(kcal) && kcal >= 500 && kcal <= 20000) saveGoal.mutate(kcal);
	}
</script>

<section class="rounded-lg border border-slate-700 p-4">
	{#if day.goalKcal === null && !editing}
		<p class="text-sm text-slate-400">Set a daily calorie limit to see how much you have left.</p>
		<p class="mt-2 text-2xl font-semibold tabular-nums">{day.consumedKcal} <span class="text-sm font-normal text-slate-400">kcal eaten</span></p>
		<button
			onclick={startEdit}
			class="mt-3 rounded-md bg-brand-500 px-3 py-1.5 text-sm font-medium text-slate-950 hover:bg-brand-400"
		>
			Set daily limit
		</button>
	{:else}
		<div class="flex items-start justify-between gap-3">
			<div>
				<p class="text-xs tracking-wide text-slate-400 uppercase">{over ? 'Over the limit by' : 'Left today'}</p>
				<p class="text-4xl font-semibold tabular-nums {over ? 'text-red-400' : 'text-emerald-400'}">
					{Math.abs(day.remainingKcal ?? 0)}
					<span class="text-base font-normal text-slate-400">kcal</span>
				</p>
			</div>
			<div class="text-right text-sm text-slate-400">
				<p><span class="tabular-nums text-slate-200">{day.consumedKcal}</span> eaten</p>
				<div>
					of
					{#if editing}
						<form
							class="inline-flex items-center gap-1"
							onsubmit={(e) => {
								e.preventDefault();
								submit();
							}}
						>
							<input
								bind:value={draft}
								type="number"
								min="500"
								max="20000"
								step="50"
								class="w-20 rounded bg-slate-800 px-2 py-0.5 text-right text-slate-100"
							/>
							<button type="submit" disabled={saveGoal.isPending} class="text-brand-400 hover:text-brand-300">Save</button>
							<button type="button" onclick={() => (editing = false)} class="text-slate-500 hover:text-slate-300">Cancel</button>
						</form>
					{:else}
						<button onclick={startEdit} class="tabular-nums text-slate-200 underline decoration-dotted hover:text-white" title="Change daily limit">
							{day.goalKcal}
						</button>
					{/if}
				</div>
			</div>
		</div>
		<div class="mt-3 h-2 overflow-hidden rounded-full bg-slate-800" role="progressbar" aria-valuenow={percent} aria-valuemin="0" aria-valuemax="100">
			<div class="h-full rounded-full {over ? 'bg-red-400' : 'bg-emerald-400'}" style="width: {percent}%"></div>
		</div>
	{/if}
	{#if saveGoal.isError}<p class="mt-2 text-sm text-red-400">{saveGoal.error.message}</p>{/if}
</section>
