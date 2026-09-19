<script lang="ts">
	import { createMutation, createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { cs2Api } from './api';
	import { PRINCIPLE_CATEGORIES, categoryLabel, type Principle, type PrincipleCategory } from './types';

	const queryClient = useQueryClient();
	const refresh = () => queryClient.invalidateQueries({ queryKey: ['gaming', 'cs2', 'principles'] });

	const principles = createQuery(() => ({
		queryKey: ['gaming', 'cs2', 'principles'],
		queryFn: () => cs2Api.principles()
	}));

	const pinned = $derived((principles.data ?? []).filter((p) => p.pinned));
	const others = $derived((principles.data ?? []).filter((p) => !p.pinned));

	let title = $state('');
	let body = $state('');
	let category = $state<PrincipleCategory>('MINDSET');

	const create = createMutation(() => ({
		mutationFn: () =>
			cs2Api.createPrinciple({ title: title.trim(), body: body.trim() || undefined, category }),
		onSuccess: () => {
			title = '';
			body = '';
			refresh();
		}
	}));

	const pin = createMutation(() => ({
		mutationFn: (v: { id: number; pinned: boolean }) => cs2Api.updatePrinciple(v.id, { pinned: v.pinned }),
		onSuccess: refresh
	}));

	const remove = createMutation(() => ({
		mutationFn: (id: number) => cs2Api.deletePrinciple(id),
		onSuccess: refresh
	}));

	function confirmDelete(p: Principle) {
		if (confirm(`Delete "${p.title}"?`)) remove.mutate(p.id);
	}
</script>

{#snippet card(p: Principle)}
	<li
		class="rounded-lg border p-3 {p.pinned
			? 'border-brand-700 bg-brand-950/30'
			: 'border-slate-700 bg-slate-800/40'}"
	>
		<div class="flex items-start gap-3">
			<div class="min-w-0 flex-1">
				<div class="font-medium">{p.title}</div>
				<span class="text-xs text-slate-500">{categoryLabel(p.category)}</span>
				{#if p.body}<p class="mt-1 text-sm whitespace-pre-line text-slate-300">{p.body}</p>{/if}
			</div>
			<button
				onclick={() => pin.mutate({ id: p.id, pinned: !p.pinned })}
				class="text-sm {p.pinned ? 'text-brand-400' : 'text-slate-500 hover:text-white'}"
				aria-pressed={p.pinned}
				title={p.pinned ? 'Unpin' : 'Pin as focus'}
			>
				{p.pinned ? '★' : '☆'}
			</button>
			<button
				onclick={() => confirmDelete(p)}
				class="text-slate-500 hover:text-red-400"
				aria-label="Delete principle">✕</button
			>
		</div>
	</li>
{/snippet}

<div class="space-y-4">
	{#if principles.isPending}
		<p class="text-slate-400">Loading…</p>
	{:else if principles.isError}
		<p class="text-red-400">Could not load principles: {principles.error.message}</p>
	{:else}
		{#if pinned.length > 0}
			<section>
				<h2 class="mb-2 text-sm font-medium tracking-wide text-brand-400 uppercase">Focus now</h2>
				<ul class="space-y-2">
					{#each pinned as p (p.id)}{@render card(p)}{/each}
				</ul>
			</section>
		{/if}

		<section>
			<h2 class="mb-2 text-sm font-medium tracking-wide text-slate-400 uppercase">All principles</h2>
			<ul class="space-y-2">
				{#each others as p (p.id)}{@render card(p)}{/each}
			</ul>
		</section>

		<form
			onsubmit={(e) => {
				e.preventDefault();
				if (title.trim()) create.mutate();
			}}
			class="space-y-2 rounded-lg border border-slate-700 p-3"
		>
			<h2 class="text-sm font-medium tracking-wide text-slate-400 uppercase">Add a principle</h2>
			<div class="flex flex-wrap gap-2">
				<input
					bind:value={title}
					placeholder="Title"
					maxlength="200"
					required
					class="min-w-40 flex-1 rounded-md bg-slate-800 px-3 py-2 text-sm"
				/>
				<select bind:value={category} class="rounded-md bg-slate-800 px-2 py-2 text-sm">
					{#each PRINCIPLE_CATEGORIES as c (c.value)}
						<option value={c.value}>{c.label}</option>
					{/each}
				</select>
			</div>
			<textarea
				bind:value={body}
				rows="2"
				maxlength="4000"
				placeholder="Details (optional)"
				class="w-full rounded-md bg-slate-800 px-3 py-2 text-sm"
			></textarea>
			<button
				type="submit"
				disabled={create.isPending}
				class="rounded-md bg-brand-500 text-slate-950 px-4 py-2 text-sm font-medium hover:bg-brand-400 disabled:opacity-50"
			>
				Add
			</button>
			{#if create.isError}<p class="text-sm text-red-400">{create.error.message}</p>{/if}
		</form>
	{/if}
</div>
