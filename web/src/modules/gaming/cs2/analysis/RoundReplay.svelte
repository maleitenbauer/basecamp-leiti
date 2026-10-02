<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { untrack } from 'svelte';
	import { SvelteMap } from 'svelte/reactivity';
	import { analysisApi } from './api';
	import RadarBackground from './RadarBackground.svelte';
	import { hasRadarCalibration, makeBboxTransform, worldToRadarPercent } from './radarCalibration';
	import type { DemoPosition, DemoRound } from './types';

	let {
		demoId,
		map,
		rounds,
		viewerSteamId
	}: { demoId: number; map: string | null; rounds: DemoRound[]; viewerSteamId: string | null } = $props();

	// initial round only — deliberately not kept in sync with `rounds` afterwards
	let selectedRound = $state<number | null>(untrack(() => rounds[0]?.number ?? null));
	let frameIndex = $state(0);
	let playing = $state(false);
	let speed = $state(1);
	let showNames = $state(true);
	let seeking = $state(false);
	// defaults to the viewer's own profile; not re-synced afterwards so it doesn't fight a manual pick
	let highlightSteamId = $state<string | null>(untrack(() => viewerSteamId));
	let size = $state<'sm' | 'md' | 'lg'>('lg');
	const sizePx = { sm: 340, md: 480, lg: 680 };

	// How many frame-steps (after the one a death was first observed in) to keep showing the death cross before
	// fading the dot out entirely. Frame-based rather than a fixed wall-clock duration so it naturally scales
	// with playback speed, same as the position easing below.
	const DEATH_MARK_FRAMES = 1;
	const diedAtFrame = new SvelteMap<string, number>();
	const deathPos = new SvelteMap<string, [number, number]>();

	const calibrated = $derived(hasRadarCalibration(map));

	const positionsQuery = createQuery(() => ({
		queryKey: ['gaming', 'cs2', 'analysis', 'demos', demoId, 'rounds', selectedRound, 'positions'],
		queryFn: () => analysisApi.getRoundPositions(demoId, selectedRound!),
		enabled: selectedRound !== null
	}));

	interface Frame {
		tick: number;
		players: DemoPosition[];
	}

	const selectedRoundMeta = $derived(rounds.find((r) => r.number === selectedRound) ?? null);

	const frames = $derived.by<Frame[]>(() => {
		const data = positionsQuery.data;
		if (!data) return [];
		// Skip buy/freeze time: every round start is really the tail of the previous round's standoff at spawn,
		// not anything worth replaying. freezeTimeEndTick is the real tick players could first move (from
		// demoinfocs-golang's RoundFreezetimeEnd event), not a guessed fixed duration — falls back to showing
		// everything for rounds parsed before this was tracked (freezeTimeEndTick null).
		const minTick = selectedRoundMeta?.freezeTimeEndTick ?? 0;
		// Safety net for an oddly short round where the whole thing fits inside one sampling interval after
		// freeze time: fall back to everything rather than showing a confusing "no data" for a round that does
		// have data.
		const hasAnyAfterMinTick = data.some((p) => p.tick >= minTick);
		const effectiveMinTick = hasAnyAfterMinTick ? minTick : 0;
		const byTick = new Map<number, DemoPosition[]>();
		for (const p of data) {
			if (p.tick < effectiveMinTick) continue;
			const bucket = byTick.get(p.tick);
			if (bucket) bucket.push(p);
			else byTick.set(p.tick, [p]);
		}
		return [...byTick.entries()]
			.sort(([a], [b]) => a - b)
			.map(([tick, players]) => ({ tick, players }));
	});

	// a fresh round starts at its first frame and auto-plays, rather than wherever the previous round left off
	$effect(() => {
		selectedRound;
		frameIndex = 0;
		playing = true;
		diedAtFrame.clear();
		deathPos.clear();
	});

	const roundIndex = $derived(rounds.findIndex((r) => r.number === selectedRound));

	function stepRound(delta: number) {
		const target = rounds[roundIndex + delta];
		if (target) selectedRound = target.number;
	}

	const intervalMs = $derived(1000 / speed);

	$effect(() => {
		if (!playing || frames.length === 0) return;
		const id = setInterval(() => {
			if (frameIndex >= frames.length - 1) {
				playing = false;
				return;
			}
			frameIndex += 1;
		}, intervalMs);
		return () => clearInterval(id);
	});

	// Samples are ~1/sec apart, so snapping straight to each one looks like jumping. While playing, ease dots
	// from their old position to the new one over the same span as the playback interval, so it reads as
	// movement instead — but not while manually scrubbing, where an in-flight transition would just add lag.
	// A single `transform` on the group (rather than separate cx/cy on the circle and x/y on the text) is what
	// keeps the name label glued to its dot: cx/cy only exist on <circle>, so a transition naming them has no
	// effect on the <text> next to it, and the label used to snap instantly while the dot eased smoothly.
	// Suppressed while actively dragging the scrubber (`seeking`) even if still playing, so a drag snaps
	// straight to each position instead of the easing fighting rapid input and feeling laggy. Opacity (used for
	// the post-death fade-out) always eases over a fixed short span regardless of play/seek state — unlike
	// position, a quick opacity fade never looks laggy, and it's what makes a death fade out instead of vanish.
	const groupTransitionStyle = $derived(
		`transition-property: transform, opacity; ` +
			`transition-duration: ${playing && !seeking ? intervalMs : 0}ms, 300ms; ` +
			`transition-timing-function: linear, ease-out;`
	);

	// Fallback for uncalibrated maps: fit the whole round's movement, computed once so the frame doesn't
	// rescale (and everything doesn't appear to jitter) as playback moves between frames.
	const fallbackTransform = $derived.by(() =>
		makeBboxTransform(frames.flatMap((f) => f.players.map((p) => ({ x: p.x, y: p.y }))))
	);

	function point(x: number, y: number): [number, number] {
		if (calibrated) {
			const p = worldToRadarPercent(map, x, y)!;
			return [p.xPct, p.yPct];
		}
		return fallbackTransform(x, y);
	}

	function teamColor(team: string): string {
		if (team === 'CT') return '#60a5fa';
		if (team === 'T') return '#fb923c';
		return '#94a3b8';
	}

	const currentFrame = $derived(frames[frameIndex] as Frame | undefined);

	// Records the first frame a player is observed dead in, and where — freezing their marker there for the
	// death-animation window below instead of relying on them still having a (possibly absent) entry in later
	// frames. Pure function of frameIndex, so scrubbing back past a death re-shows the cross naturally; no
	// explicit re-trigger logic needed.
	$effect(() => {
		const frame = currentFrame;
		if (!frame) return;
		for (const p of frame.players) {
			if (!p.alive && !diedAtFrame.has(p.steamId)) {
				diedAtFrame.set(p.steamId, frameIndex);
				deathPos.set(p.steamId, point(p.x, p.y));
			}
		}
	});

	// A fixed set of dots for the whole round, mounted once — instead of an {#each} keyed directly off
	// currentFrame.players. That would destroy and recreate a player's <circle> for any frame where they're
	// briefly missing from the sample (a real gap in the data, not necessarily about dying), and a freshly
	// mounted element has no previous position for the CSS transition below to ease from, so it just snaps.
	// Keeping the same element for the whole round and only moving/hiding it means the transition never breaks.
	const roster = $derived.by(() => {
		const byId = new Map<string, { steamId: string; name: string; team: string }>();
		for (const f of frames) {
			for (const p of f.players) {
				if (!byId.has(p.steamId)) byId.set(p.steamId, { steamId: p.steamId, name: p.name, team: p.team });
			}
		}
		return [...byId.values()];
	});

	const currentByPlayer = $derived.by(() => {
		const m = new Map<string, DemoPosition>();
		for (const p of currentFrame?.players ?? []) m.set(p.steamId, p);
		return m;
	});

	// Same roster, but with the highlighted player's entry moved last — later in the SVG document order paints
	// on top, so their dot never ends up hidden under an overlapping one. Reordering an existing keyed {#each}
	// item moves its DOM node rather than recreating it, so this doesn't interrupt the position transition.
	const orderedRoster = $derived.by(() => {
		if (!highlightSteamId) return roster;
		const idx = roster.findIndex((r) => r.steamId === highlightSteamId);
		if (idx === -1) return roster;
		const reordered = [...roster];
		const [highlighted] = reordered.splice(idx, 1);
		reordered.push(highlighted);
		return reordered;
	});
</script>

<div class="space-y-2">
	<div class="flex flex-wrap items-center gap-3 text-xs text-slate-400">
		<div class="flex items-center gap-1">
			<button
				onclick={() => stepRound(-1)}
				disabled={roundIndex <= 0}
				class="rounded border border-slate-700 px-2 py-1 hover:bg-slate-800 disabled:opacity-30 disabled:hover:bg-transparent"
				title="Previous round"
			>
				◀
			</button>
			<select
				bind:value={selectedRound}
				class="rounded border border-slate-700 bg-slate-900 px-2 py-1 text-slate-200"
			>
				{#each rounds as r (r.number)}
					<option value={r.number}>
						Round {r.number} ({r.winnerTeam ?? 'tie'} · CT {r.ctScore}:{r.tScore} T)
					</option>
				{/each}
			</select>
			<button
				onclick={() => stepRound(1)}
				disabled={roundIndex === -1 || roundIndex >= rounds.length - 1}
				class="rounded border border-slate-700 px-2 py-1 hover:bg-slate-800 disabled:opacity-30 disabled:hover:bg-transparent"
				title="Next round"
			>
				▶
			</button>
		</div>

		<select
			bind:value={highlightSteamId}
			class="rounded border border-slate-700 bg-slate-900 px-2 py-1 text-slate-200"
		>
			<option value={null}>No highlight</option>
			{#each roster as r (r.steamId)}
				<option value={r.steamId}>{r.name}{r.steamId === viewerSteamId ? ' (me)' : ''}</option>
			{/each}
		</select>

		<label class="flex items-center gap-1.5">
			<input type="checkbox" bind:checked={showNames} class="h-3.5 w-3.5" />
			Names
		</label>

		<div class="flex items-center gap-1">
			{#each [0.5, 1, 2, 4] as const as s (s)}
				<button
					onclick={() => (speed = s)}
					class="rounded px-2 py-1 {speed === s ? 'bg-brand-500/20 text-brand-300' : 'hover:bg-slate-800'}"
				>
					{s}x
				</button>
			{/each}
		</div>

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

	{#if positionsQuery.isPending}
		<p class="text-sm text-slate-400">Loading positions…</p>
	{:else if positionsQuery.isError}
		<p class="text-sm text-red-400">{positionsQuery.error.message}</p>
	{:else if frames.length === 0}
		<p class="text-sm text-slate-500">No position data for this round.</p>
	{:else}
		<div
			class="relative mx-auto aspect-square overflow-hidden rounded-lg border border-slate-700 bg-slate-950"
			style="width: {sizePx[size]}px; max-width: 100%;"
		>
			<RadarBackground {map} />

			<svg viewBox="0 0 100 100" class="absolute inset-0 h-full w-full">
				{#each orderedRoster as r (r.steamId)}
					{@const p = currentByPlayer.get(r.steamId)}
					{@const alive = p !== undefined && p.alive}
					{@const diedFrame = diedAtFrame.get(r.steamId)}
					{@const justDied = diedFrame !== undefined && frameIndex - diedFrame >= 0 && frameIndex - diedFrame <= DEATH_MARK_FRAMES}
					{@const visible = alive || justDied}
					{@const [x, y] = alive ? point(p.x, p.y) : (deathPos.get(r.steamId) ?? [0, 0])}
					{@const isHighlighted = r.steamId === highlightSteamId}
					{@const dimmed = highlightSteamId !== null && !isHighlighted}
					{@const radius = isHighlighted ? 2.6 : 1.8}
					<g
						transform="translate({x} {y})"
						opacity={visible ? (dimmed ? 0.35 : 1) : 0}
						style={groupTransitionStyle}
					>
						<circle
							r={radius}
							fill={teamColor(r.team)}
							fill-opacity={justDied ? 0.5 : 1}
							stroke={isHighlighted ? '#facc15' : '#0f172a'}
							stroke-width={isHighlighted ? 0.6 : 0.3}
						>
							<title>{r.name} ({r.team}){p ? ` — ${p.health} HP` : ''}</title>
						</circle>
						{#if justDied}
							<g stroke="#ef4444" stroke-width="0.7" stroke-linecap="round">
								<line x1={-radius} y1={-radius} x2={radius} y2={radius} />
								<line x1={-radius} y1={radius} x2={radius} y2={-radius} />
							</g>
						{/if}
						{#if showNames || isHighlighted}
							<text
								y={-(radius + 0.6)}
								font-size={isHighlighted ? 3 : 2.4}
								font-weight={isHighlighted ? 'bold' : 'normal'}
								text-anchor="middle"
								fill={isHighlighted ? '#facc15' : '#e2e8f0'}
								stroke="#0f172a"
								stroke-width="0.3"
								paint-order="stroke"
							>
								{r.name.slice(0, 12)}
							</text>
						{/if}
					</g>
				{/each}
			</svg>
		</div>

		<div class="flex items-center gap-2 text-xs text-slate-400">
			<button
				onclick={() => (playing = !playing)}
				class="rounded border border-slate-700 px-2 py-1 hover:bg-slate-800"
			>
				{playing ? 'Pause' : 'Play'}
			</button>
			<input
				type="range"
				min="0"
				max={frames.length - 1}
				bind:value={frameIndex}
				onpointerdown={() => (seeking = true)}
				onpointerup={() => (seeking = false)}
				onpointercancel={() => (seeking = false)}
				class="flex-1"
			/>
			<span class="w-28 shrink-0 text-right tabular-nums">
				{frameIndex + 1}/{frames.length} · tick {currentFrame?.tick ?? 0}
			</span>
		</div>
		<p class="text-xs text-slate-500">
			{calibrated ? `Real ${map} coordinates.` : 'No radar calibration for this map — positions are relative to this round only.'}
			Sampled roughly once per second, not every tick — dots ease smoothly between samples while playing, but the
			path in between is a straight-line guess, not what actually happened. Starts right after buy/freeze time
			ends, skipping the standoff at spawn.
		</p>
	{/if}
</div>
