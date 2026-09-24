<script lang="ts">
	import { createMutation, useQueryClient } from '@tanstack/svelte-query';
	import { shoppingApi } from './api';
	import MovePicker from './MovePicker.svelte';
	import type { ShoppingItem } from './types';

	let { item, otherLists }: { item: ShoppingItem; otherLists: { id: number; name: string }[] } = $props();

	const queryClient = useQueryClient();
	const refresh = () => queryClient.invalidateQueries({ queryKey: ['lifestyle', 'shopping'] });

	const update = createMutation(() => ({
		mutationFn: (body: Parameters<typeof shoppingApi.updateItem>[1]) => shoppingApi.updateItem(item.id, body),
		onSuccess: refresh
	}));

	const remove = createMutation(() => ({
		mutationFn: () => shoppingApi.deleteItem(item.id),
		onSuccess: refresh
	}));

	const move = createMutation(() => ({
		mutationFn: (target: Parameters<typeof shoppingApi.moveItem>[1]) => shoppingApi.moveItem(item.id, target),
		onSuccess: refresh
	}));

	// the +/- stepper is hidden until first used; quantity null/1 both mean "just get it"
	function bump(delta: number) {
		const next = (item.quantity ?? 1) + delta;
		if (next <= 1) update.mutate({ clearQuantity: true });
		else update.mutate({ quantity: next });
	}
</script>

<li class="flex flex-wrap items-center gap-2 py-2">
	<input
		type="checkbox"
		checked={item.checked}
		onchange={(e) => update.mutate({ checked: e.currentTarget.checked })}
		class="h-5 w-5 shrink-0"
		aria-label={item.checked ? `Reopen: ${item.name}` : `Got it: ${item.name}`}
	/>

	<div class="min-w-0 flex-1">
		<span class="break-words {item.checked ? 'text-slate-500 line-through' : ''}">{item.name}</span>
		{#if item.unit}
			<span class="ml-2 text-xs text-slate-500">{item.unit}</span>
		{/if}
	</div>

	<div class="flex shrink-0 items-center gap-1">
		{#if item.quantity}
			<button
				onclick={() => bump(-1)}
				class="h-6 w-6 rounded bg-slate-800 text-sm hover:bg-slate-700"
				aria-label="Fewer">−</button
			>
			<span class="w-5 text-center text-sm tabular-nums">{item.quantity}</span>
		{/if}
		<button onclick={() => bump(1)} class="h-6 w-6 rounded bg-slate-800 text-sm hover:bg-slate-700" aria-label="More"
			>+</button
		>
	</div>

	<MovePicker {otherLists} onMove={(target) => move.mutate(target)} />

	<button onclick={() => remove.mutate()} class="shrink-0 text-slate-500 hover:text-red-400" aria-label="Remove">
		✕
	</button>
</li>
{#if update.isError}<p class="text-xs text-red-400">{update.error.message}</p>{/if}
{#if move.isError}<p class="text-xs text-red-400">{move.error.message}</p>{/if}
