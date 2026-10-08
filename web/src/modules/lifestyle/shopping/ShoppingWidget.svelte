<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { shoppingApi } from './api';

	const MAX_SHOWN = 4;

	// the same query as the Shopping page
	const lists = createQuery(() => ({
		queryKey: ['lifestyle', 'shopping', 'lists'],
		queryFn: () => shoppingApi.listAll()
	}));

	const active = $derived(
		(lists.data?.active ?? []).map((l) => ({ id: l.id, name: l.name, toBuy: l.items.filter((i) => !i.checked).length }))
	);
</script>

{#if lists.isPending}
	<p class="text-sm text-slate-400">Loading…</p>
{:else if lists.isError}
	<p class="text-sm text-red-400">{lists.error.message}</p>
{:else if active.length === 0}
	<p class="text-sm text-slate-400">
		No open shopping lists. <a href="/lifestyle/shopping" class="text-brand-400 hover:text-brand-300">Start one</a>
	</p>
{:else}
	<ul class="space-y-1.5">
		{#each active.slice(0, MAX_SHOWN) as list (list.id)}
			<li class="flex items-center justify-between gap-3 text-sm">
				<span class="min-w-0 truncate">{list.name}</span>
				<span class="shrink-0 text-xs {list.toBuy === 0 ? 'text-emerald-400' : 'text-slate-400'}">
					{list.toBuy === 0 ? 'all checked' : `${list.toBuy} to buy`}
				</span>
			</li>
		{/each}
	</ul>
	{#if active.length > MAX_SHOWN}
		<a href="/lifestyle/shopping" class="mt-2 inline-block text-xs text-slate-500 hover:text-slate-300">
			+ {active.length - MAX_SHOWN} more lists
		</a>
	{/if}
{/if}
