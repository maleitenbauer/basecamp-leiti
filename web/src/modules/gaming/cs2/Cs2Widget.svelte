<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { today } from '$lib/dates';
	import { cs2Api } from './api';

	const MAX_SHOWN = 4;
	const date = today();

	// the same query as the CS2 improvement routine tab
	const routine = createQuery(() => ({
		queryKey: ['gaming', 'cs2', 'routine', date],
		queryFn: () => cs2Api.routine(date)
	}));

	const active = $derived((routine.data?.items ?? []).filter((i) => i.active));
	const remaining = $derived(active.filter((i) => !i.done));
</script>

{#if routine.isPending}
	<p class="text-sm text-slate-400">Loading…</p>
{:else if routine.isError}
	<p class="text-sm text-red-400">{routine.error.message}</p>
{:else if active.length === 0}
	<p class="text-sm text-slate-400">
		No practice routine yet. <a href="/gaming/cs2/improvement" class="text-brand-400 hover:text-brand-300">Set one up</a>
	</p>
{:else}
	<p class="text-sm {remaining.length === 0 ? 'text-emerald-400' : 'text-slate-300'}">
		{remaining.length === 0 ? 'Practice routine done for today' : `${active.length - remaining.length} of ${active.length} practice items done`}
		{#if routine.data.streak > 0}<span class="text-slate-500"> · {routine.data.streak} day streak</span>{/if}
	</p>
	{#if remaining.length > 0}
		<ul class="mt-2 space-y-1.5">
			{#each remaining.slice(0, MAX_SHOWN) as item (item.id)}
				<li class="flex items-center gap-2 text-sm">
					<span class="min-w-0 flex-1 truncate">{item.title}</span>
					{#if item.targetMinutes}<span class="shrink-0 text-xs text-slate-500">{item.targetMinutes} min</span>{/if}
				</li>
			{/each}
		</ul>
		{#if remaining.length > MAX_SHOWN}
			<a href="/gaming/cs2/improvement" class="mt-2 inline-block text-xs text-slate-500 hover:text-slate-300">
				+ {remaining.length - MAX_SHOWN} more
			</a>
		{/if}
	{/if}
{/if}
