<script lang="ts">
	import { createMutation, createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { today } from '$lib/dates';
	import { routineApi } from './api';

	const MAX_SHOWN = 5;
	const date = today();
	const queryClient = useQueryClient();

	// the same query as the Routine page, so checking something off here or there updates both
	const overview = createQuery(() => ({
		queryKey: ['lifestyle', 'routine', 'overview', date],
		queryFn: () => routineApi.overview(date)
	}));

	const markDone = createMutation(() => ({
		mutationFn: (id: number) => routineApi.markDone(id, date),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ['lifestyle', 'routine'] })
	}));

	const routines = $derived(overview.data?.routines ?? []);
	const open = $derived(routines.filter((r) => r.state === 'OPEN'));
</script>

{#if overview.isPending}
	<p class="text-sm text-slate-400">Loading…</p>
{:else if overview.isError}
	<p class="text-sm text-red-400">{overview.error.message}</p>
{:else if routines.length === 0}
	<p class="text-sm text-slate-400">
		No routines yet. <a href="/lifestyle/routine" class="text-brand-400 hover:text-brand-300">Add one</a>
	</p>
{:else if open.length === 0}
	<p class="text-sm text-emerald-400">Everything is done for today.</p>
{:else}
	<p class="text-sm text-slate-300">{open.length} still open</p>
	<ul class="mt-2 space-y-1.5">
		{#each open.slice(0, MAX_SHOWN) as routine (routine.id)}
			<li class="flex items-center gap-2 text-sm">
				<button
					onclick={() => markDone.mutate(routine.id)}
					disabled={markDone.isPending}
					aria-label="Mark {routine.name} done"
					class="h-5 w-5 shrink-0 rounded-full border-2 border-slate-600 hover:border-brand-400"
				></button>
				<span class="min-w-0 truncate">{routine.name}</span>
				{#if routine.targetMinutes}<span class="shrink-0 text-xs text-slate-500">{routine.targetMinutes} min</span>{/if}
				{#if routine.overdueSince}<span class="shrink-0 text-xs text-amber-400">overdue</span>{/if}
			</li>
		{/each}
	</ul>
	{#if open.length > MAX_SHOWN}
		<a href="/lifestyle/routine" class="mt-2 inline-block text-xs text-slate-500 hover:text-slate-300">
			+ {open.length - MAX_SHOWN} more
		</a>
	{/if}
{/if}
