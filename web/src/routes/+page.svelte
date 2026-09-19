<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { modules } from '$modules/registry';

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

	<div class="mt-6 grid gap-3 sm:grid-cols-2">
		{#each modules as m (m.id)}
			<a href={m.href} class="rounded-lg border border-slate-700 p-4 hover:border-slate-500">
				<div class="text-2xl">{m.icon}</div>
				<div class="mt-1 font-medium">{m.name}</div>
				<div class="text-sm text-slate-400">{m.description}</div>
			</a>
		{/each}
	</div>

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
