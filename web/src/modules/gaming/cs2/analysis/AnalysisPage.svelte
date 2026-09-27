<script lang="ts">
	import { createMutation, createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { analysisApi } from './api';
	import KillScatter from './KillScatter.svelte';
	import { formatBytes, type DemoStatus } from './types';

	const queryClient = useQueryClient();
	const refresh = () => queryClient.invalidateQueries({ queryKey: ['gaming', 'cs2', 'analysis'] });

	const available = createQuery(() => ({
		queryKey: ['gaming', 'cs2', 'analysis', 'available'],
		queryFn: () => analysisApi.available()
	}));

	const demos = createQuery(() => ({
		queryKey: ['gaming', 'cs2', 'analysis', 'demos'],
		queryFn: () => analysisApi.listDemos(),
		// keep polling while anything is still being parsed, so status updates show up without a manual refresh
		refetchInterval: (query) => (query.state.data?.some((d) => d.status === 'PARSING' || d.status === 'UPLOADED') ? 2000 : false)
	}));

	let selectedId = $state<number | null>(null);
	const analysis = createQuery(() => ({
		queryKey: ['gaming', 'cs2', 'analysis', 'demos', selectedId],
		queryFn: () => analysisApi.getAnalysis(selectedId!),
		enabled: selectedId !== null
	}));

	let fileInput: HTMLInputElement | undefined = $state();
	const upload = createMutation(() => ({
		mutationFn: (file: File) => analysisApi.uploadDemo(file),
		onSuccess: () => {
			selectedId = null;
			if (fileInput) fileInput.value = '';
			refresh();
		}
	}));

	function onFileChosen(e: Event) {
		const file = (e.target as HTMLInputElement).files?.[0];
		if (file) upload.mutate(file);
	}

	const retry = createMutation(() => ({ mutationFn: (id: number) => analysisApi.retry(id), onSuccess: refresh }));
	const remove = createMutation(() => ({
		mutationFn: (id: number) => analysisApi.deleteDemo(id),
		onSuccess: () => {
			if (selectedId !== null) selectedId = null;
			refresh();
		}
	}));

	const statusLabel: Record<DemoStatus, string> = {
		UPLOADED: 'Queued',
		PARSING: 'Parsing…',
		READY: 'Ready',
		FAILED: 'Failed'
	};
	const statusTone: Record<DemoStatus, string> = {
		UPLOADED: 'text-slate-400',
		PARSING: 'text-brand-400',
		READY: 'text-emerald-400',
		FAILED: 'text-red-400'
	};
</script>

<div class="space-y-4">
	<div>
		<h2 class="text-lg font-semibold">2D Analysis</h2>
		<p class="mt-1 text-sm text-slate-400">
			Upload a .dem from a match and see where you get kills and deaths. Round-by-round position data is
			stored too, for an animated replay in a later update.
		</p>
	</div>

	{#if available.data && !available.data.available}
		<p class="rounded-lg border border-amber-800 bg-amber-950/30 p-3 text-sm text-amber-300">
			The analysis tool isn't available on this server yet. This only works once deployed via Docker (it needs
			a component built from <code class="text-amber-200">analysis-parser/</code>), not in local dev.
		</p>
	{/if}

	<label
		class="flex cursor-pointer items-center justify-center rounded-lg border border-dashed border-slate-700 p-6 text-sm text-slate-400 hover:border-slate-500 hover:text-slate-300"
	>
		{upload.isPending ? 'Uploading…' : 'Click to choose a .dem file'}
		<input
			bind:this={fileInput}
			type="file"
			accept=".dem"
			onchange={onFileChosen}
			disabled={upload.isPending}
			class="hidden"
		/>
	</label>
	{#if upload.isError}<p class="text-sm text-red-400">{upload.error.message}</p>{/if}

	{#if demos.isPending}
		<p class="text-slate-400">Loading…</p>
	{:else if demos.isError}
		<p class="text-red-400">Could not load your demos: {demos.error.message}</p>
	{:else if demos.data.length === 0}
		<p class="text-slate-500">No demos uploaded yet.</p>
	{:else}
		<ul class="divide-y divide-slate-800 rounded-lg border border-slate-700">
			{#each demos.data as demo (demo.id)}
				<li class="p-3">
					<div class="flex flex-wrap items-center gap-3">
						<button
							onclick={() => (selectedId = demo.status === 'READY' ? demo.id : selectedId)}
							disabled={demo.status !== 'READY'}
							class="min-w-0 flex-1 truncate text-left text-sm {demo.status === 'READY'
								? 'hover:text-brand-400'
								: ''}"
						>
							{demo.originalFilename}
							{#if demo.map}<span class="text-slate-500">· {demo.map}</span>{/if}
							<span class="text-xs text-slate-500">· {formatBytes(demo.sizeBytes)}</span>
						</button>
						<span class="text-xs font-medium {statusTone[demo.status]}">{statusLabel[demo.status]}</span>
						{#if demo.status === 'READY'}
							<span class="text-xs text-slate-500">{demo.roundCount} rounds · {demo.killCount} kills</span>
						{/if}
						{#if demo.status === 'FAILED'}
							<button onclick={() => retry.mutate(demo.id)} class="text-xs text-brand-400 hover:text-brand-300">
								Retry
							</button>
						{/if}
						<button
							onclick={() => confirm(`Delete "${demo.originalFilename}"?`) && remove.mutate(demo.id)}
							class="text-xs text-slate-500 hover:text-red-400"
						>
							Delete
						</button>
					</div>
					{#if demo.status === 'FAILED' && demo.errorMessage}
						<p class="mt-1 text-xs text-red-400">{demo.errorMessage}</p>
					{/if}

					{#if selectedId === demo.id}
						<div class="mt-3 border-t border-slate-800 pt-3">
							{#if analysis.isPending}
								<p class="text-sm text-slate-400">Loading analysis…</p>
							{:else if analysis.isError}
								<p class="text-sm text-red-400">{analysis.error.message}</p>
							{:else}
								<KillScatter
									kills={analysis.data.kills}
									map={analysis.data.map}
									viewerSteamId={analysis.data.viewerSteamId}
								/>
								<p class="mt-2 text-xs text-slate-500">
									{analysis.data.rounds.length} rounds parsed. Grenades: {analysis.data.grenades.length}.
								</p>
							{/if}
						</div>
					{/if}
				</li>
			{/each}
		</ul>
	{/if}
</div>
