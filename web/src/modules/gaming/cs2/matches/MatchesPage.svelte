<script lang="ts">
	import { createMutation, createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { matchesApi } from './api';
	import MatchList from './MatchList.svelte';
	import MatchSettings from './MatchSettings.svelte';

	type Tab = 'faceit' | 'leetify';

	const tabs: { id: Tab; label: string }[] = [
		{ id: 'faceit', label: 'FACEIT' },
		{ id: 'leetify', label: 'Matchmaking' }
	];

	const queryClient = useQueryClient();
	let tab = $state<Tab>('faceit');

	const settings = createQuery(() => ({
		queryKey: ['gaming', 'cs2', 'matches', 'settings'],
		queryFn: () => matchesApi.settings()
	}));

	const faceit = createQuery(() => ({
		queryKey: ['gaming', 'cs2', 'matches', 'faceit'],
		queryFn: () => matchesApi.faceit(),
		enabled: tab === 'faceit',
		staleTime: 60_000,
		retry: false
	}));

	const leetify = createQuery(() => ({
		queryKey: ['gaming', 'cs2', 'matches', 'leetify'],
		queryFn: () => matchesApi.leetify(),
		enabled: tab === 'leetify',
		staleTime: 60_000,
		retry: false
	}));

	const refreshFaceit = createMutation(() => ({
		mutationFn: () => matchesApi.faceit(true),
		onSuccess: (data) => queryClient.setQueryData(['gaming', 'cs2', 'matches', 'faceit'], data)
	}));

	const refreshLeetify = createMutation(() => ({
		mutationFn: () => matchesApi.leetify(true),
		onSuccess: (data) => queryClient.setQueryData(['gaming', 'cs2', 'matches', 'leetify'], data)
	}));

	const current = $derived(tab === 'faceit' ? faceit : leetify);
	const needsSetup = $derived(current.data?.configured === false);
</script>

<main class="mx-auto max-w-4xl p-4 sm:p-6">
	<h1 class="text-2xl font-semibold">Matches</h1>
	<p class="mt-1 text-sm text-slate-400">Your recent Counter-Strike 2 matches and how you played.</p>

	<div class="mt-4 inline-flex rounded-lg bg-slate-800 p-1 text-sm" role="tablist">
		{#each tabs as t (t.id)}
			<button
				role="tab"
				aria-selected={tab === t.id}
				onclick={() => (tab = t.id)}
				class="rounded-md px-3 py-1.5 whitespace-nowrap {tab === t.id
					? 'bg-slate-600 text-white'
					: 'text-slate-400 hover:text-white'}"
			>
				{t.label}
			</button>
		{/each}
	</div>

	<div class="mt-4 space-y-4">
		{#if current.isPending}
			<p class="text-slate-400">Loading matches…</p>
		{:else if current.isError}
			<p class="rounded-lg border border-red-900 bg-red-950/30 p-3 text-red-300" role="alert">
				{current.error.message}
			</p>
		{:else if needsSetup}
			<p class="rounded-lg border border-brand-700 bg-brand-950/30 p-3 text-brand-200">
				{current.data.message}
			</p>
		{:else if tab === 'faceit'}
			<MatchList
				data={faceit.data!}
				provider="faceit"
				refreshing={refreshFaceit.isPending}
				onRefresh={() => refreshFaceit.mutate()}
			/>
		{:else}
			<MatchList
				data={leetify.data!}
				provider="leetify"
				refreshing={refreshLeetify.isPending}
				onRefresh={() => refreshLeetify.mutate()}
			/>
		{/if}
		{#if refreshFaceit.isError}<p class="text-sm text-red-400">{refreshFaceit.error.message}</p>{/if}
		{#if refreshLeetify.isError}<p class="text-sm text-red-400">{refreshLeetify.error.message}</p>{/if}

		{#if settings.data}
			{#key settings.data}
				<MatchSettings settings={settings.data} open={needsSetup || current.isError} />
			{/key}
		{/if}
	</div>
</main>
