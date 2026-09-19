<script lang="ts">
	import PrinciplesTab from './PrinciplesTab.svelte';
	import ReviewsTab from './ReviewsTab.svelte';
	import RoutineTab from './RoutineTab.svelte';

	type Tab = 'routine' | 'reviews' | 'principles';

	const tabs: { id: Tab; label: string }[] = [
		{ id: 'routine', label: 'Daily routine' },
		{ id: 'reviews', label: 'Session reviews' },
		{ id: 'principles', label: 'Coach principles' }
	];

	let tab = $state<Tab>('routine');
</script>

<main class="mx-auto max-w-4xl p-4 sm:p-6">
	<h1 class="text-2xl font-semibold">Improvement</h1>
	<p class="mt-1 text-sm text-slate-400">
		Train the habits your coach pointed out. Warm up, play with focus, review afterwards.
	</p>

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

	<div class="mt-4">
		{#if tab === 'routine'}
			<RoutineTab />
		{:else if tab === 'reviews'}
			<ReviewsTab />
		{:else}
			<PrinciplesTab />
		{/if}
	</div>
</main>
