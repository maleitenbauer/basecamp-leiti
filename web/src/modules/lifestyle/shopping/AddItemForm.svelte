<script lang="ts">
	import { createMutation, useQueryClient } from '@tanstack/svelte-query';
	import { shoppingApi } from './api';
	import type { KnownItem } from './types';

	let { listId, knownItems }: { listId: number; knownItems: KnownItem[] } = $props();

	const queryClient = useQueryClient();

	let name = $state('');
	let highlighted = $state(0);
	let inputEl: HTMLInputElement | undefined = $state();

	// known items are already sorted by use (most-used first); just filter by what's typed
	const suggestions = $derived.by(() => {
		const query = name.trim().toLowerCase();
		if (!query) return [];
		return knownItems.filter((k) => k.name.toLowerCase().includes(query)).slice(0, 8);
	});
	const showExactMatch = $derived(
		suggestions.some((s) => s.name.toLowerCase() === name.trim().toLowerCase())
	);

	const add = createMutation(() => ({
		mutationFn: (itemName: string) => shoppingApi.addItem(listId, { name: itemName }),
		onSuccess: () => {
			name = '';
			highlighted = 0;
			queryClient.invalidateQueries({ queryKey: ['lifestyle', 'shopping'] });
		}
	}));

	function submit(itemName?: string) {
		const value = (itemName ?? name).trim();
		if (value) add.mutate(value);
	}

	function onKeydown(e: KeyboardEvent) {
		if (suggestions.length === 0) return;
		if (e.key === 'ArrowDown') {
			e.preventDefault();
			highlighted = Math.min(highlighted + 1, suggestions.length - 1);
		} else if (e.key === 'ArrowUp') {
			e.preventDefault();
			highlighted = Math.max(highlighted - 1, 0);
		} else if (e.key === 'Enter' && highlighted < suggestions.length) {
			e.preventDefault();
			submit(suggestions[highlighted].name);
		}
	}
</script>

<form
	onsubmit={(e) => {
		e.preventDefault();
		submit();
	}}
	class="relative"
>
	<div class="flex gap-2">
		<input
			bind:this={inputEl}
			bind:value={name}
			oninput={() => (highlighted = 0)}
			onkeydown={onKeydown}
			placeholder="Add an item…"
			maxlength="200"
			autocomplete="off"
			class="min-w-0 flex-1 rounded-md bg-slate-800 px-3 py-2 text-sm"
		/>
		<button
			type="submit"
			disabled={add.isPending || !name.trim()}
			class="rounded-md bg-brand-500 px-4 py-2 text-sm font-medium text-slate-950 hover:bg-brand-400 disabled:opacity-50"
		>
			Add
		</button>
	</div>

	{#if suggestions.length > 0}
		<ul class="absolute z-10 mt-1 w-full rounded-md border border-slate-700 bg-slate-900 shadow-xl">
			{#each suggestions as s, i (s.name)}
				<li>
					<button
						type="button"
						onclick={() => submit(s.name)}
						onmouseenter={() => (highlighted = i)}
						class="block w-full truncate px-3 py-2 text-left text-sm {i === highlighted
							? 'bg-slate-800'
							: 'hover:bg-slate-800'}"
					>
						{s.name}
					</button>
				</li>
			{/each}
			{#if name.trim() && !showExactMatch}
				<li class="border-t border-slate-800">
					<button
						type="button"
						onclick={() => submit()}
						class="block w-full px-3 py-2 text-left text-sm text-brand-400 hover:bg-slate-800"
					>
						Add "{name.trim()}"
					</button>
				</li>
			{/if}
		</ul>
	{/if}
	{#if add.isError}<p class="mt-1 text-sm text-red-400">{add.error.message}</p>{/if}
</form>
