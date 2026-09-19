<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';

	const ping = createQuery(() => ({
		queryKey: ['ping'],
		queryFn: async () => {
			const res = await fetch('/api/ping');
			if (!res.ok) throw new Error(`HTTP ${res.status}`);
			return (await res.json()) as { app: string; status: string; time: string };
		},
		refetchInterval: 10_000,
		retry: false
	}));
</script>

<main class="mx-auto max-w-3xl p-6">
	<h1 class="text-3xl font-semibold">Basecamp</h1>
	<p class="mt-2 text-slate-400">Dashboard shell — modules will appear here.</p>

	<section class="mt-8 rounded-lg border border-slate-700 p-4" data-testid="backend-status">
		<h2 class="text-sm font-medium tracking-wide text-slate-400 uppercase">Backend</h2>
		{#if ping.isPending}
			<p class="mt-1">Checking…</p>
		{:else if ping.isError}
			<p class="mt-1 text-red-400">Unreachable ({ping.error.message})</p>
		{:else}
			<p class="mt-1 text-emerald-400">
				Reachable — {ping.data.app} is {ping.data.status}
				<span class="text-slate-500">({new Date(ping.data.time).toLocaleTimeString()})</span>
			</p>
		{/if}
	</section>
</main>
