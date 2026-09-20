<script lang="ts">
	import MapArt from './MapArt.svelte';
	import {
		formatLeetifyRating,
		prettyMap,
		ratingTone,
		type MatchesResponse,
		type MatchResult
	} from './types';

	let {
		data,
		provider,
		refreshing = false,
		onRefresh
	}: {
		data: MatchesResponse;
		provider: 'faceit' | 'leetify';
		refreshing?: boolean;
		onRefresh: () => void;
	} = $props();

	const wins = $derived(data.matches.filter((m) => m.result === 'WIN').length);
	const losses = $derived(data.matches.filter((m) => m.result === 'LOSS').length);
	const winPercent = $derived(wins + losses > 0 ? Math.round((wins / (wins + losses)) * 100) : null);
	const form = $derived(data.matches.slice(0, 10));

	const maps = $derived.by(() => {
		const acc = new Map<string, { raw: string | null; played: number; wins: number; losses: number }>();
		for (const m of data.matches) {
			const key = prettyMap(m.map);
			const row = acc.get(key) ?? { raw: m.map, played: 0, wins: 0, losses: 0 };
			row.played += 1;
			if (m.result === 'WIN') row.wins += 1;
			if (m.result === 'LOSS') row.losses += 1;
			acc.set(key, row);
		}
		return [...acc.entries()]
			.map(([map, r]) => ({ map, ...r }))
			.sort((a, b) => b.played - a.played);
	});

	const badge: Record<MatchResult, string> = {
		WIN: 'bg-emerald-900/60 text-emerald-300',
		LOSS: 'bg-red-900/60 text-red-300',
		DRAW: 'bg-slate-700 text-slate-300'
	};
	const dot: Record<MatchResult, string> = {
		WIN: 'bg-emerald-500',
		LOSS: 'bg-red-500',
		DRAW: 'bg-slate-500'
	};

	const num = (n: number | null, digits = 1) => (n === null ? '–' : n.toFixed(digits));
	const day = (iso: string | null) =>
		iso ? new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short' }) : '';
	const time = (iso: string) => new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
</script>

<div class="space-y-4">
	<div class="flex flex-wrap items-center gap-3">
		{#if data.player}
			<div class="text-sm">
				<span class="font-medium">{data.player.nickname}</span>
				{#if data.player.level !== null}
					<span class="ml-2 rounded bg-brand-500 px-1.5 py-0.5 text-xs font-semibold text-slate-950">
						Lv {data.player.level}
					</span>
				{/if}
				{#if data.player.elo !== null}<span class="ml-2 text-slate-400">{data.player.elo} ELO</span>{/if}
			</div>
		{/if}
		<div class="ml-auto flex items-center gap-3 text-xs text-slate-500">
			<span>Updated {time(data.fetchedAt)}</span>
			<button
				onclick={onRefresh}
				disabled={refreshing}
				class="rounded-md bg-slate-800 px-3 py-1.5 text-sm text-slate-200 hover:bg-slate-700 disabled:opacity-50"
			>
				{refreshing ? 'Refreshing…' : 'Refresh'}
			</button>
		</div>
	</div>

	{#if data.matches.length === 0}
		<p class="rounded-lg border border-slate-800 p-4 text-slate-400">
			No recent {provider === 'faceit' ? 'FACEIT' : 'matchmaking'} matches found for this account.
		</p>
	{:else}
		<section class="flex flex-wrap items-center gap-6 rounded-lg border border-slate-700 p-3">
			<div>
				<div class="text-2xl font-semibold">
					<span class="text-emerald-400">{wins}</span>
					<span class="text-slate-500">–</span>
					<span class="text-red-400">{losses}</span>
				</div>
				<div class="text-xs text-slate-400">last {data.matches.length} matches</div>
			</div>
			<div>
				<div class="text-2xl font-semibold">{winPercent === null ? '–' : `${winPercent}%`}</div>
				<div class="text-xs text-slate-400">win rate</div>
			</div>
			<div class="ml-auto" role="img" aria-label="Form, newest first">
				<div class="flex gap-1">
					{#each form as m (m.id)}
						<span
							class="h-3 w-3 rounded-full {m.result ? dot[m.result] : 'bg-slate-700'}"
							title="{prettyMap(m.map)} {m.score ?? ''}"
						></span>
					{/each}
				</div>
				<div class="mt-1 text-right text-xs text-slate-500">form, newest first</div>
			</div>
		</section>

		{#if maps.length > 1}
			<section class="rounded-lg border border-slate-700 p-3">
				<h2 class="mb-2 text-sm font-medium tracking-wide text-slate-400 uppercase">By map</h2>
				<ul class="grid gap-x-6 gap-y-1 text-sm sm:grid-cols-2">
					{#each maps as row (row.map)}
						<li class="flex items-center justify-between">
							<span class="flex items-center gap-2">
								<span class="h-6 w-6 shrink-0 overflow-hidden rounded opacity-75"><MapArt map={row.raw} /></span>
								{row.map}
							</span>
							<span class="text-slate-400">
								{row.played} played ·
								<span class="text-emerald-400">{row.wins}W</span>
								<span class="text-red-400">{row.losses}L</span>
							</span>
						</li>
					{/each}
				</ul>
			</section>
		{/if}

		<ul class="space-y-2">
			{#each data.matches as m (m.id)}
				<li class="relative overflow-hidden rounded-lg border border-slate-700 bg-slate-800/40">
					<!-- map art fades out towards the stats -->
					<div
						class="pointer-events-none absolute inset-y-0 left-0 w-44 opacity-45 sm:w-64"
						style="mask-image: linear-gradient(to right, #000 25%, transparent); -webkit-mask-image: linear-gradient(to right, #000 25%, transparent)"
					>
						<MapArt map={m.map} />
					</div>
					<div class="relative flex flex-wrap items-center gap-x-4 gap-y-2 p-3">
						<span
							class="w-14 rounded-md px-2 py-1 text-center text-xs font-semibold {m.result
								? badge[m.result]
								: 'bg-slate-800 text-slate-500'}"
						>
							{m.result ?? '?'}
						</span>
						<div class="min-w-32 flex-1 [text-shadow:0_1px_4px_rgb(0_0_0/0.65)]">
							<div class="font-semibold">{prettyMap(m.map)}</div>
							<div class="text-xs text-slate-300">
								{day(m.finishedAt)}{m.mode ? ` · ${m.mode}` : ''}{m.score ? ` · ${m.score}` : ''}
							</div>
						</div>
						<dl class="flex flex-wrap gap-x-5 gap-y-1 text-sm">
							{#if m.kills !== null}
								<div>
									<dt class="text-xs text-slate-500">K / D / A</dt>
									<dd>{m.kills} / {m.deaths ?? '–'} / {m.assists ?? '–'}</dd>
								</div>
							{/if}
							{#if m.kdRatio !== null}
								<div>
									<dt class="text-xs text-slate-500">K/D</dt>
									<dd>{num(m.kdRatio, 2)}</dd>
								</div>
							{/if}
							{#if m.adr !== null}
								<div>
									<dt class="text-xs text-slate-500">ADR</dt>
									<dd>{num(m.adr, 0)}</dd>
								</div>
							{/if}
							{#if m.headshotPercent !== null}
								<div>
									<dt class="text-xs text-slate-500">HS</dt>
									<dd>{num(m.headshotPercent, 0)}%</dd>
								</div>
							{:else if m.headshotKills !== null}
								<div>
									<dt class="text-xs text-slate-500">HS kills</dt>
									<dd>{m.headshotKills}</dd>
								</div>
							{/if}
							{#if m.rating !== null}
								<div>
									<dt class="text-xs text-slate-500">{m.ratingLabel ?? 'Rating'}</dt>
									<dd class="font-semibold {ratingTone(m.rating)}">{formatLeetifyRating(m.rating)}</dd>
								</div>
							{/if}
						</dl>
						{#if m.url}
							<a
								href={m.url}
								target="_blank"
								rel="noopener noreferrer"
								class="text-xs text-brand-400 hover:text-brand-300"
							>
								{provider === 'leetify' ? 'View on Leetify' : 'Open'} ↗
							</a>
						{/if}
					</div>
					{#if m.details}
						<details class="relative px-3 pb-3 text-xs text-slate-400">
							<summary class="cursor-pointer">All stats</summary>
							<dl class="mt-2 grid grid-cols-2 gap-x-4 gap-y-0.5 sm:grid-cols-3">
								{#each Object.entries(m.details) as [key, value] (key)}
									<div class="flex justify-between gap-2">
										<dt class="truncate text-slate-500">{key}</dt>
										<dd class="text-slate-300">{value}</dd>
									</div>
								{/each}
							</dl>
						</details>
					{/if}
				</li>
			{/each}
		</ul>
	{/if}

	{#if provider === 'leetify'}
		<p class="text-xs text-slate-500">
			Data provided by Leetify ·
			<a
				href={data.sourceUrl ?? 'https://leetify.com'}
				target="_blank"
				rel="noopener noreferrer"
				class="text-brand-400 hover:text-brand-300">View on Leetify</a
			>
		</p>
	{/if}
</div>
