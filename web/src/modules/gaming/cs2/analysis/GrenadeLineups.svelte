<script lang="ts">
	import { untrack } from 'svelte';
	import RadarBackground from './RadarBackground.svelte';
	import { hasRadarCalibration, makeBboxTransform, worldToRadarPercent } from './radarCalibration';
	import type { DemoGrenade } from './types';

	let { grenades, map, viewerSteamId }: { grenades: DemoGrenade[]; map: string | null; viewerSteamId: string | null } =
		$props();

	const calibrated = $derived(hasRadarCalibration(map));

	const throwers = $derived.by(() => {
		const byId = new Map<string, { steamId: string; name: string; team: string | null }>();
		for (const g of grenades) {
			if (g.throwerSteamId && g.throwerName) {
				byId.set(g.throwerSteamId, { steamId: g.throwerSteamId, name: g.throwerName, team: g.throwerTeam });
			}
		}
		return [...byId.values()].sort((a, b) => a.name.localeCompare(b.name));
	});

	// default to the viewer's own profile if they appear in this demo, otherwise "all players" — a one-time
	// default on mount, deliberately not kept in sync afterwards so it doesn't fight a manual selection
	let selectedSteamId = $state<string | null>(
		untrack(() => (viewerSteamId && throwers.some((t) => t.steamId === viewerSteamId) ? viewerSteamId : null))
	);

	const GRENADE_TYPES = ['Flash', 'Smoke', 'HE', 'Molotov', 'Incendiary', 'Decoy'] as const;
	let enabledTypes = $state<Record<string, boolean>>(Object.fromEntries(GRENADE_TYPES.map((t) => [t, true])));

	let size = $state<'sm' | 'md' | 'lg'>('lg');
	const sizePx = { sm: 340, md: 480, lg: 680 };

	const visibleGrenades = $derived(
		grenades.filter(
			(g) =>
				enabledTypes[g.type] !== false &&
				(selectedSteamId === null || g.throwerSteamId === selectedSteamId) &&
				g.trajectory.length > 0
		)
	);

	// Fallback for maps with no radar calibration: scale to the bounding box of whatever's currently visible, so
	// a single player's throws fill the plot instead of being lost in the whole match's spread.
	const fallbackTransform = $derived.by(() =>
		makeBboxTransform(visibleGrenades.flatMap((g) => g.trajectory))
	);

	function point(x: number, y: number): [number, number] {
		if (calibrated) {
			const p = worldToRadarPercent(map, x, y)!;
			return [p.xPct, p.yPct];
		}
		return fallbackTransform(x, y);
	}

	// Matches the colors demoinfocs-golang's own nade-trajectories example uses, for anyone used to reading one.
	function typeColor(type: string): string {
		switch (type) {
			case 'Flash':
				return '#60a5fa';
			case 'Smoke':
				return '#cbd5e1';
			case 'HE':
				return '#4ade80';
			case 'Molotov':
			case 'Incendiary':
				return '#f97316';
			case 'Decoy':
				return '#a16207';
			default:
				return '#e2e8f0';
		}
	}
</script>

<div class="space-y-2">
	<div class="flex flex-wrap items-center gap-3 text-xs text-slate-400">
		<select
			bind:value={selectedSteamId}
			class="rounded border border-slate-700 bg-slate-900 px-2 py-1 text-slate-200"
		>
			<option value={null}>All players</option>
			{#each throwers as t (t.steamId)}
				<option value={t.steamId}>{t.name}{t.steamId === viewerSteamId ? ' (me)' : ''}</option>
			{/each}
		</select>

		{#each GRENADE_TYPES as t (t)}
			<label class="flex items-center gap-1.5">
				<input type="checkbox" bind:checked={enabledTypes[t]} class="h-3.5 w-3.5" />
				<span class="inline-block h-2 w-2 rounded-full" style="background: {typeColor(t)}"></span>
				{t}
			</label>
		{/each}

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
		class="relative mx-auto aspect-square overflow-hidden rounded-lg border border-slate-700 bg-slate-950"
		style="width: {sizePx[size]}px; max-width: 100%;"
	>
		<RadarBackground {map} />

		<svg viewBox="0 0 100 100" class="absolute inset-0 h-full w-full">
			{#each visibleGrenades as g, i (i)}
				{@const pathPoints = g.trajectory.map((pt) => point(pt.x, pt.y))}
				{@const pathD = 'M ' + pathPoints.map(([x, y]) => `${x} ${y}`).join(' L ')}
				{@const [throwPx, throwPy] = pathPoints[0]}
				{@const [detPx, detPy] = pathPoints[pathPoints.length - 1]}
				<path d={pathD} fill="none" stroke={typeColor(g.type)} stroke-width="0.4" stroke-opacity="0.6" />
				<circle cx={throwPx} cy={throwPy} r="0.9" fill="none" stroke={typeColor(g.type)} stroke-width="0.5">
					<title>{g.throwerName ?? 'Unknown'} threw a {g.type} — round {g.round}</title>
				</circle>
				<circle cx={detPx} cy={detPy} r="1.3" fill={typeColor(g.type)} fill-opacity="0.9">
					<title>{g.type} from {g.throwerName ?? 'unknown'} landed here — round {g.round}</title>
				</circle>
			{/each}
		</svg>
	</div>
	<p class="text-xs text-slate-500">
		{#if calibrated}
			Real {map} coordinates.
		{:else}
			{map ? `No radar calibration for ${map} yet — showing` : 'Showing'} an abstract grid scaled to what's currently
			visible, not a real map layout.
		{/if}
		Hollow circle = thrown from here, filled circle = landed here, line = actual flight path.
	</p>
</div>
