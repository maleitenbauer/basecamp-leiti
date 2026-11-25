<script lang="ts">
	import { kg, minutesText } from './format';
	import { finishMessage } from './motivation';
	import type { FinishResult } from './types';

	/** Shown right after a workout ends: a warm message, what was done, personal bests and the week's progress. */
	let { result, onClose }: { result: FinishResult; onClose: () => void } = $props();

	const summary = $derived(result.summary);
	const stats = $derived(result.stats);
	const message = $derived(
		finishMessage({
			date: summary.day,
			minutes: summary.minutes,
			sets: summary.sets,
			personalBests: result.personalBests.length,
			workoutsThisWeek: stats.workoutsThisWeek,
			weeklyTarget: stats.weeklyTarget,
			autoEnded: summary.endedBy === 'AUTO'
		})
	);
	const dots = $derived(
		stats.weeklyTarget === null
			? []
			: Array.from({ length: Math.max(stats.weeklyTarget, stats.workoutsThisWeek) }, (_, i) => i < stats.workoutsThisWeek)
	);
</script>

<section class="space-y-4 rounded-lg border border-emerald-500/40 bg-emerald-500/10 p-4">
	<div>
		<h2 class="text-xl font-semibold">{message.headline}</h2>
		<p class="mt-1 text-slate-300">{message.line}</p>
	</div>

	<dl class="grid grid-cols-3 gap-2 text-center">
		<div class="rounded-md bg-slate-900/60 p-2">
			<dt class="text-xs text-slate-400">Time</dt>
			<dd class="font-semibold">{minutesText(summary.minutes)}</dd>
		</div>
		<div class="rounded-md bg-slate-900/60 p-2">
			<dt class="text-xs text-slate-400">Sets</dt>
			<dd class="font-semibold">{summary.sets}</dd>
		</div>
		<div class="rounded-md bg-slate-900/60 p-2">
			<dt class="text-xs text-slate-400">Weight moved</dt>
			<dd class="font-semibold">{summary.volumeKg > 0 ? `${Math.round(summary.volumeKg)} kg` : 'n/a'}</dd>
		</div>
	</dl>

	{#if result.personalBests.length > 0}
		<ul class="space-y-1 text-sm">
			{#each result.personalBests as best (best.exercise)}
				<li class="text-amber-300">
					🏅 {best.exercise}: {best.weightKg ? `${best.reps} × ${kg(best.weightKg)}` : `${best.reps} reps`}
				</li>
			{/each}
		</ul>
	{/if}

	{#if dots.length > 0}
		<div class="flex items-center gap-2 text-sm text-slate-300">
			<span class="flex gap-1" role="img" aria-label="{stats.workoutsThisWeek} of {stats.weeklyTarget} workouts this week">
				{#each dots as filled, i (i)}
					<span class="h-2.5 w-2.5 rounded-full {filled ? 'bg-emerald-400' : 'bg-slate-700'}"></span>
				{/each}
			</span>
			{stats.workoutsThisWeek} of {stats.weeklyTarget} this week
		</div>
	{:else}
		<p class="text-sm text-slate-400">{stats.workoutsThisWeek} workout{stats.workoutsThisWeek === 1 ? '' : 's'} this week.</p>
	{/if}

	{#if result.routineCompleted}
		<p class="text-sm text-emerald-300">✓ "{result.routineCompleted}" is ticked off in your routines.</p>
	{/if}

	<button onclick={onClose} class="rounded-md bg-slate-800 px-4 py-2 text-sm hover:bg-slate-700">Done</button>
</section>
