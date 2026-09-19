<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { today } from '$lib/dates';
	import { cs2Api } from './api';
	import { categoryLabel } from './types';

	const day = today();

	const routine = createQuery(() => ({
		queryKey: ['gaming', 'cs2', 'routine', day],
		queryFn: () => cs2Api.routine(day)
	}));

	const principles = createQuery(() => ({
		queryKey: ['gaming', 'cs2', 'principles'],
		queryFn: () => cs2Api.principles()
	}));

	const active = $derived((routine.data?.items ?? []).filter((i) => i.active));
	const open = $derived(active.filter((i) => !i.done));
	const pinned = $derived((principles.data ?? []).filter((p) => p.pinned));
</script>

<main class="mx-auto max-w-4xl space-y-4 p-4 sm:p-6">
	<h1 class="text-2xl font-semibold">Counter-Strike 2</h1>

	<section class="rounded-lg border border-slate-700 p-4">
		<div class="flex items-center justify-between">
			<h2 class="text-sm font-medium tracking-wide text-slate-400 uppercase">Today's routine</h2>
			<a href="/gaming/cs2/improvement" class="text-sm text-emerald-400 hover:text-emerald-300">Open →</a>
		</div>
		{#if routine.isPending}
			<p class="mt-2 text-slate-400">Loading…</p>
		{:else if routine.isError}
			<p class="mt-2 text-red-400">Could not load: {routine.error.message}</p>
		{:else}
			<p class="mt-2">
				<span class="text-2xl font-semibold">{active.length - open.length}/{active.length}</span>
				<span class="text-slate-400"> done · 🔥 {routine.data.streak} day streak</span>
			</p>
			{#if open.length > 0}
				<ul class="mt-2 space-y-1 text-sm text-slate-300">
					{#each open.slice(0, 4) as item (item.id)}
						<li>○ {item.title}</li>
					{/each}
					{#if open.length > 4}<li class="text-slate-500">+ {open.length - 4} more</li>{/if}
				</ul>
			{:else if active.length > 0}
				<p class="mt-2 text-sm text-emerald-400">Everything done for today.</p>
			{/if}
		{/if}
	</section>

	{#if pinned.length > 0}
		<section class="rounded-lg border border-emerald-700 bg-emerald-950/30 p-4">
			<h2 class="text-sm font-medium tracking-wide text-emerald-400 uppercase">Focus now</h2>
			<ul class="mt-2 space-y-3">
				{#each pinned as p (p.id)}
					<li>
						<div class="font-medium">{p.title} <span class="text-xs text-slate-500">{categoryLabel(p.category)}</span></div>
						{#if p.body}<p class="text-sm text-slate-300">{p.body}</p>{/if}
					</li>
				{/each}
			</ul>
		</section>
	{/if}
</main>
