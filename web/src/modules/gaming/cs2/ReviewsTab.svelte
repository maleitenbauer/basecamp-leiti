<script lang="ts">
	import { createMutation, createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { today } from '$lib/dates';
	import { cs2Api } from './api';

	const queryClient = useQueryClient();
	const refresh = () => queryClient.invalidateQueries({ queryKey: ['gaming', 'cs2', 'reviews'] });

	const reviews = createQuery(() => ({
		queryKey: ['gaming', 'cs2', 'reviews'],
		queryFn: () => cs2Api.reviews()
	}));

	const ratings = [
		{ key: 'focus', label: 'Focus', hint: 'Was I ready for every duel?' },
		{ key: 'movement', label: 'Movement', hint: 'Did I avoid holding W in duels?' },
		{ key: 'utility', label: 'Utility', hint: 'Did every throw have a purpose?' }
	] as const;

	let playedOn = $state(today());
	let values = $state({ focus: 3, movement: 3, utility: 3 });
	let notes = $state('');

	const create = createMutation(() => ({
		mutationFn: () =>
			cs2Api.createReview({
				playedOn: playedOn || undefined,
				focus: values.focus,
				movement: values.movement,
				utility: values.utility,
				notes: notes.trim() || undefined
			}),
		onSuccess: () => {
			notes = '';
			values = { focus: 3, movement: 3, utility: 3 };
			refresh();
		}
	}));

	const remove = createMutation(() => ({
		mutationFn: (id: number) => cs2Api.deleteReview(id),
		onSuccess: refresh
	}));

	const fmt = (n: number) => n.toFixed(1);
</script>

<div class="space-y-4">
	<form
		onsubmit={(e) => {
			e.preventDefault();
			create.mutate();
		}}
		class="space-y-3 rounded-lg border border-slate-700 p-3"
	>
		<div class="flex items-center gap-2">
			<h2 class="text-sm font-medium tracking-wide text-slate-400 uppercase">Review a session</h2>
			<input
				type="date"
				bind:value={playedOn}
				max={today()}
				class="ml-auto rounded-md bg-slate-800 px-2 py-1.5 text-sm"
				aria-label="Day played"
			/>
		</div>

		{#each ratings as r (r.key)}
			<label class="block">
				<div class="flex items-baseline justify-between text-sm">
					<span class="font-medium">{r.label}</span>
					<span class="text-slate-400">{values[r.key]} / 5</span>
				</div>
				<div class="text-xs text-slate-500">{r.hint}</div>
				<input
					type="range"
					min="1"
					max="5"
					step="1"
					bind:value={values[r.key]}
					class="mt-1 w-full accent-brand-500"
				/>
			</label>
		{/each}

		<textarea
			bind:value={notes}
			rows="3"
			maxlength="4000"
			placeholder="What went well, what to fix next time?"
			class="w-full rounded-md bg-slate-800 px-3 py-2 text-sm"
		></textarea>
		<button
			type="submit"
			disabled={create.isPending}
			class="rounded-md bg-brand-500 text-slate-950 px-4 py-2 text-sm font-medium hover:bg-brand-400 disabled:opacity-50"
		>
			Save review
		</button>
		{#if create.isError}<p class="text-sm text-red-400">{create.error.message}</p>{/if}
	</form>

	{#if reviews.isPending}
		<p class="text-slate-400">Loading…</p>
	{:else if reviews.isError}
		<p class="text-red-400">Could not load reviews: {reviews.error.message}</p>
	{:else}
		{#if reviews.data.averages}
			{@const a = reviews.data.averages}
			<section class="grid grid-cols-3 gap-3 text-center">
				{#each [{ label: 'Focus', value: a.focus }, { label: 'Movement', value: a.movement }, { label: 'Utility', value: a.utility }] as s (s.label)}
					<div class="rounded-lg border border-slate-700 p-3">
						<div class="text-2xl font-semibold">{fmt(s.value)}</div>
						<div class="text-xs text-slate-400">{s.label} · last {a.count}</div>
					</div>
				{/each}
			</section>
		{/if}

		{#if reviews.data.reviews.length === 0}
			<p class="text-slate-500">No reviews yet. Rate your next session right after you play.</p>
		{:else}
			<ul class="space-y-2">
				{#each reviews.data.reviews as r (r.id)}
					<li class="rounded-lg border border-slate-700 bg-slate-800/40 p-3 text-sm">
						<div class="flex items-center gap-3">
							<span class="font-medium">{r.playedOn}</span>
							<span class="text-slate-400">
								Focus {r.focus} · Movement {r.movement} · Utility {r.utility}
							</span>
							<button
								onclick={() => confirm('Delete this review?') && remove.mutate(r.id)}
								class="ml-auto text-slate-500 hover:text-red-400"
								aria-label="Delete review">✕</button
							>
						</div>
						{#if r.notes}<p class="mt-1 whitespace-pre-line text-slate-300">{r.notes}</p>{/if}
					</li>
				{/each}
			</ul>
		{/if}
	{/if}
</div>
