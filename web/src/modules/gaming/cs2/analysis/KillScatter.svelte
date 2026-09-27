<script module lang="ts">
	// remember which optional radar images do not exist, so re-rendering this demo doesn't re-request them
	const missingRadar = new Set<string>();
</script>

<script lang="ts">
	import { untrack } from 'svelte';
	import { mapSlug } from '../matches/maps';
	import { hasRadarCalibration, worldToRadarPercent } from './radarCalibration';
	import type { DemoKill } from './types';

	let { kills, map, viewerSteamId }: { kills: DemoKill[]; map: string | null; viewerSteamId: string | null } =
		$props();

	const slug = $derived(mapSlug(map));
	const calibrated = $derived(hasRadarCalibration(map));

	const players = $derived.by(() => {
		const byId = new Map<string, { steamId: string; name: string; team: string | null }>();
		for (const k of kills) {
			if (k.attackerSteamId && k.attackerName) {
				byId.set(k.attackerSteamId, { steamId: k.attackerSteamId, name: k.attackerName, team: k.attackerTeam });
			}
			if (k.victimSteamId && k.victimName) {
				byId.set(k.victimSteamId, { steamId: k.victimSteamId, name: k.victimName, team: k.victimTeam });
			}
		}
		return [...byId.values()].sort((a, b) => a.name.localeCompare(b.name));
	});

	// default to the viewer's own profile if they appear in this demo, otherwise "all players" — a one-time
	// default on mount, deliberately not kept in sync afterwards so it doesn't fight a manual selection
	let selectedSteamId = $state<string | null>(
		untrack(() => (viewerSteamId && players.some((p) => p.steamId === viewerSteamId) ? viewerSteamId : null))
	);

	let showKills = $state(true);
	let showDeaths = $state(true);
	let onlyHeadshots = $state(false);
	let size = $state<'sm' | 'md' | 'lg'>('sm');
	const sizePx = { sm: 340, md: 480, lg: 680 };

	let radarFailed = $state(false);
	const showRadarImage = $derived(calibrated && !radarFailed && !missingRadar.has(slug));

	const passesFilters = (k: DemoKill) => !onlyHeadshots || k.headshot;
	const myKills = $derived(
		kills.filter((k) => passesFilters(k) && (selectedSteamId === null || k.attackerSteamId === selectedSteamId))
	);
	const myDeaths = $derived(
		kills.filter((k) => passesFilters(k) && (selectedSteamId === null || k.victimSteamId === selectedSteamId))
	);

	// Fallback for maps with no radar calibration: scale to the bounding box of whatever's currently visible,
	// so a single player's kills/deaths fill the plot instead of being lost in the whole match's spread.
	const fallbackTransform = $derived.by(() => {
		const xs: number[] = [];
		const ys: number[] = [];
		for (const k of showKills ? myKills : []) {
			if (k.attackerX !== null && k.attackerY !== null) {
				xs.push(k.attackerX);
				ys.push(k.attackerY);
			}
		}
		for (const k of showDeaths ? myDeaths : []) {
			xs.push(k.victimX);
			ys.push(k.victimY);
		}
		const minX = xs.length ? Math.min(...xs) : 0;
		const maxX = xs.length ? Math.max(...xs) : 1;
		const minY = ys.length ? Math.min(...ys) : 0;
		const maxY = ys.length ? Math.max(...ys) : 1;
		const spanX = maxX - minX || 1;
		const spanY = maxY - minY || 1;
		// SVG y grows downward; game Y typically grows "north", so flip it for a more intuitive top-down feel.
		return (x: number, y: number): [number, number] => [((x - minX) / spanX) * 100, 100 - ((y - minY) / spanY) * 100];
	});

	function point(x: number, y: number): [number, number] {
		if (calibrated) {
			const p = worldToRadarPercent(map, x, y)!;
			return [p.xPct, p.yPct];
		}
		return fallbackTransform(x, y);
	}
</script>

<div class="space-y-2">
	<div class="flex flex-wrap items-center gap-3 text-xs text-slate-400">
		<select
			bind:value={selectedSteamId}
			class="rounded border border-slate-700 bg-slate-900 px-2 py-1 text-slate-200"
		>
			<option value={null}>All players</option>
			{#each players as p (p.steamId)}
				<option value={p.steamId}>{p.name}{p.steamId === viewerSteamId ? ' (me)' : ''}</option>
			{/each}
		</select>

		<label class="flex items-center gap-1.5">
			<input type="checkbox" bind:checked={showKills} class="h-3.5 w-3.5" />
			<span class="inline-block h-2 w-2 rounded-full bg-emerald-400"></span>
			{selectedSteamId === null ? 'Kills' : 'Kills (by them)'}
		</label>
		<label class="flex items-center gap-1.5">
			<input type="checkbox" bind:checked={showDeaths} class="h-3.5 w-3.5" />
			<span class="inline-block h-2 w-2 rounded-full bg-red-400"></span>
			{selectedSteamId === null ? 'Deaths' : 'Deaths (theirs)'}
		</label>
		<label class="flex items-center gap-1.5">
			<input type="checkbox" bind:checked={onlyHeadshots} class="h-3.5 w-3.5" />
			Headshots only
		</label>

		<div class="ml-auto flex items-center gap-1">
			{#each ['sm', 'md', 'lg'] as const as s (s)}
				<button
					onclick={() => (size = s)}
					class="rounded px-2 py-1 {size === s ? 'bg-brand-500/20 text-brand-300' : 'hover:bg-slate-800'}"
				>
					{s.toUpperCase()}
				</button>
			{/each}
		</div>
	</div>

	<div
		class="relative aspect-square overflow-hidden rounded-lg border border-slate-700 bg-slate-950"
		style="width: {sizePx[size]}px; max-width: 100%;"
	>
		{#if showRadarImage}
			<img
				src="/maps/radar/{slug}.png"
				alt=""
				class="absolute inset-0 h-full w-full object-cover"
				onerror={() => {
					missingRadar.add(slug);
					radarFailed = true;
				}}
			/>
		{:else if !calibrated}
			<svg viewBox="0 0 100 100" class="absolute inset-0 h-full w-full text-slate-700" aria-hidden="true">
				{#each [20, 40, 60, 80] as g (g)}
					<line x1={g} y1="0" x2={g} y2="100" stroke="currentColor" stroke-width="0.2" />
					<line x1="0" y1={g} x2="100" y2={g} stroke="currentColor" stroke-width="0.2" />
				{/each}
			</svg>
		{/if}

		<svg viewBox="0 0 100 100" class="absolute inset-0 h-full w-full">
			{#if showKills}
				{#each myKills as k, i (i)}
					{#if k.attackerX !== null && k.attackerY !== null}
						{@const [x, y] = point(k.attackerX, k.attackerY)}
						<circle cx={x} cy={y} r={k.headshot ? 1.6 : 1.1} fill="#34d399" fill-opacity="0.85">
							<title>{k.attackerName ?? 'Unknown'} killed {k.victimName ?? 'unknown'} ({k.weapon}{k.headshot ? ', HS' : ''}) — round {k.round}</title>
						</circle>
					{/if}
				{/each}
			{/if}
			{#if showDeaths}
				{#each myDeaths as k, i (i)}
					{@const [x, y] = point(k.victimX, k.victimY)}
					<circle cx={x} cy={y} r="1.1" fill="#f87171" fill-opacity="0.75">
						<title>{k.victimName ?? 'Unknown'} died to {k.attackerName ?? 'unknown'} ({k.weapon}) — round {k.round}</title>
					</circle>
				{/each}
			{/if}
		</svg>
	</div>
	<p class="text-xs text-slate-500">
		{#if calibrated}
			Real {map} coordinates.{!showRadarImage
				? ` Add your own radar image at web/static/maps/radar/${slug}.png to see it as the background.`
				: ''}
		{:else}
			{map ? `No radar calibration for ${map} yet — showing` : 'Showing'} an abstract grid scaled to what's currently
			visible, not a real map layout.
		{/if}
	</p>
</div>
