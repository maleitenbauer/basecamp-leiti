<script lang="ts">
	import { createMutation, useQueryClient } from '@tanstack/svelte-query';
	import { nutritionApi } from './api';
	import type { MealEntry } from './types';

	let { entry }: { entry: MealEntry } = $props();

	const queryClient = useQueryClient();
	const refresh = () => queryClient.invalidateQueries({ queryKey: ['lifestyle', 'fitness', 'nutrition'] });

	// Food-based entries are edited by amount (kcal follows); quick entries by their kcal directly.
	const byGrams = $derived(entry.grams !== null);

	let editing = $state(false);
	let draft = $state('');

	const update = createMutation(() => ({
		mutationFn: (value: number) =>
			nutritionApi.updateEntry(entry.id, byGrams ? { grams: value } : { kcal: Math.round(value) }),
		onSuccess: () => {
			editing = false;
			refresh();
		}
	}));
	const remove = createMutation(() => ({ mutationFn: () => nutritionApi.deleteEntry(entry.id), onSuccess: refresh }));

	const time = $derived(new Date(entry.eatenAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }));

	function startEdit() {
		draft = String(byGrams ? entry.grams : entry.kcal);
		editing = true;
	}

	function submit() {
		const value = Number(draft);
		if (Number.isFinite(value) && value > 0) update.mutate(value);
	}
</script>

<li class="flex items-center gap-3 px-3 py-2 text-sm">
	<span class="w-12 shrink-0 text-xs text-slate-500 tabular-nums">{time}</span>
	<span class="min-w-0 flex-1 truncate">{entry.name}</span>

	{#if editing}
		<form
			class="flex items-center gap-1"
			onsubmit={(e) => {
				e.preventDefault();
				submit();
			}}
		>
			<input bind:value={draft} type="number" min="0.1" step="any" class="w-20 rounded bg-slate-800 px-2 py-0.5 text-right" />
			<span class="text-xs text-slate-500">{byGrams ? 'g' : 'kcal'}</span>
			<button type="submit" disabled={update.isPending} class="text-xs text-brand-400 hover:text-brand-300">Save</button>
			<button type="button" onclick={() => (editing = false)} class="text-xs text-slate-500 hover:text-slate-300">Cancel</button>
		</form>
	{:else}
		{#if byGrams}<span class="text-xs text-slate-500 tabular-nums">{entry.grams} g</span>{/if}
		<span class="w-20 text-right font-medium tabular-nums">{entry.kcal} kcal</span>
		<button onclick={startEdit} class="text-xs text-slate-500 hover:text-slate-200" title="Edit amount">Edit</button>
		<button
			onclick={() => remove.mutate()}
			disabled={remove.isPending}
			class="text-xs text-slate-500 hover:text-red-400"
			title="Remove"
		>
			Remove
		</button>
	{/if}
</li>
