<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { today } from '$lib/dates';
	import { todosApi } from './api';
	import { dueBadge, dueGroup } from './types';

	const MAX_SHOWN = 4;
	const now = today();

	// the same query as the Todos page
	const list = createQuery(() => ({
		queryKey: ['lifestyle', 'todos'],
		queryFn: () => todosApi.list()
	}));

	// overdue first (oldest first), then what's due today
	const urgent = $derived(
		(list.data?.open ?? [])
			.filter((t) => ['overdue', 'today'].includes(dueGroup(t.dueDate, now)))
			.sort((a, b) => (a.dueDate ?? '').localeCompare(b.dueDate ?? ''))
	);
	const overdueCount = $derived(urgent.filter((t) => dueGroup(t.dueDate, now) === 'overdue').length);
</script>

{#if list.isPending}
	<p class="text-sm text-slate-400">Loading…</p>
{:else if list.isError}
	<p class="text-sm text-red-400">{list.error.message}</p>
{:else if urgent.length === 0}
	<p class="text-sm {list.data.open.length === 0 ? 'text-slate-400' : 'text-emerald-400'}">
		{list.data.open.length === 0 ? 'No open todos.' : 'Nothing due today or overdue.'}
	</p>
	{#if list.data.open.length > 0}
		<p class="mt-1 text-xs text-slate-500">{list.data.open.length} open in total</p>
	{/if}
{:else}
	<p class="text-sm text-slate-300">
		{urgent.length} due{#if overdueCount > 0}, <span class="text-red-400">{overdueCount} overdue</span>{/if}
	</p>
	<ul class="mt-2 space-y-1.5">
		{#each urgent.slice(0, MAX_SHOWN) as todo (todo.id)}
			<li class="flex items-center gap-2 text-sm">
				<span class="min-w-0 flex-1 truncate">{todo.title}</span>
				{#if todo.dueDate}
					{@const badge = dueBadge(todo.dueDate, now)}
					<span class="shrink-0 rounded px-1.5 py-0.5 text-xs {badge.tone}">{badge.text}</span>
				{/if}
			</li>
		{/each}
	</ul>
	{#if urgent.length > MAX_SHOWN}
		<a href="/lifestyle/todos" class="mt-2 inline-block text-xs text-slate-500 hover:text-slate-300">
			+ {urgent.length - MAX_SHOWN} more
		</a>
	{/if}
{/if}
