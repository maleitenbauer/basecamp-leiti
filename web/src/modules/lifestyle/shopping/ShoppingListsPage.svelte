<script lang="ts">
	import { createMutation, createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { shoppingApi } from './api';
	import ShoppingListCard from './ShoppingListCard.svelte';
	import TemplateCard from './TemplateCard.svelte';

	const queryClient = useQueryClient();

	const lists = createQuery(() => ({
		queryKey: ['lifestyle', 'shopping', 'lists'],
		queryFn: () => shoppingApi.listAll()
	}));

	const known = createQuery(() => ({
		queryKey: ['lifestyle', 'shopping', 'known-items'],
		queryFn: () => shoppingApi.knownItems()
	}));

	const templates = createQuery(() => ({
		queryKey: ['lifestyle', 'shopping', 'templates'],
		queryFn: () => shoppingApi.listTemplates()
	}));

	let newListName = $state('');

	const create = createMutation(() => ({
		mutationFn: () => shoppingApi.createList({ name: newListName.trim() }),
		onSuccess: () => {
			newListName = '';
			queryClient.invalidateQueries({ queryKey: ['lifestyle', 'shopping'] });
		}
	}));

	let newTemplateName = $state('');
	let addingTemplate = $state(false);

	const createTemplate = createMutation(() => ({
		mutationFn: () => shoppingApi.createTemplate({ name: newTemplateName.trim() }),
		onSuccess: () => {
			newTemplateName = '';
			addingTemplate = false;
			queryClient.invalidateQueries({ queryKey: ['lifestyle', 'shopping', 'templates'] });
		}
	}));
</script>

<main class="mx-auto max-w-2xl p-4 sm:p-6">
	<h1 class="text-2xl font-semibold">Shopping List</h1>
	<p class="mt-1 text-sm text-slate-400">
		Keep one list per store, add items as you think of them, and move what's missing to another list.
	</p>

	<form
		onsubmit={(e) => {
			e.preventDefault();
			if (newListName.trim()) create.mutate();
		}}
		class="mt-4 flex gap-2"
	>
		<input
			bind:value={newListName}
			placeholder="New list, e.g. Rewe, Weekly groceries…"
			maxlength="200"
			required
			class="min-w-0 flex-1 rounded-md bg-slate-800 px-3 py-2 text-sm"
		/>
		<button
			type="submit"
			disabled={create.isPending}
			class="rounded-md bg-brand-500 px-4 py-2 text-sm font-medium text-slate-950 hover:bg-brand-400 disabled:opacity-50"
		>
			Create
		</button>
	</form>
	{#if create.isError}<p class="mt-1 text-sm text-red-400">{create.error.message}</p>{/if}

	<section class="mt-6">
		<div class="flex items-center justify-between">
			<h2 class="text-sm font-medium tracking-wide text-slate-400 uppercase">Recurring templates</h2>
			<button onclick={() => (addingTemplate = !addingTemplate)} class="text-xs text-brand-400 hover:text-brand-300">
				{addingTemplate ? 'Cancel' : '+ New template'}
			</button>
		</div>
		<p class="mt-1 text-xs text-slate-500">
			Save a list you shop often as a template, then use it to start a fresh list in one click.
		</p>

		{#if addingTemplate}
			<form
				onsubmit={(e) => {
					e.preventDefault();
					if (newTemplateName.trim()) createTemplate.mutate();
				}}
				class="mt-2 flex gap-2"
			>
				<input
					bind:value={newTemplateName}
					placeholder="Template name, e.g. Weekly staples…"
					maxlength="200"
					required
					class="min-w-0 flex-1 rounded-md bg-slate-800 px-3 py-2 text-sm"
				/>
				<button
					type="submit"
					disabled={createTemplate.isPending}
					class="rounded-md bg-slate-700 px-4 py-2 text-sm hover:bg-slate-600"
				>
					Create
				</button>
			</form>
			{#if createTemplate.isError}<p class="mt-1 text-sm text-red-400">{createTemplate.error.message}</p>{/if}
		{/if}

		<div class="mt-3 space-y-2">
			{#if templates.isPending}
				<p class="text-sm text-slate-400">Loading…</p>
			{:else if templates.isError}
				<p class="text-sm text-red-400">Could not load templates: {templates.error.message}</p>
			{:else if templates.data.length === 0}
				<p class="text-sm text-slate-500">
					No templates yet. Create one above, or save an existing list as a template below.
				</p>
			{:else}
				{#each templates.data as template (template.id)}
					<TemplateCard {template} />
				{/each}
			{/if}
		</div>
	</section>

	<div class="mt-6 space-y-3">
		{#if lists.isPending}
			<p class="text-slate-400">Loading…</p>
		{:else if lists.isError}
			<p class="text-red-400">Could not load your lists: {lists.error.message}</p>
		{:else}
			{#if lists.data.active.length === 0}
				<p class="text-slate-500">No active lists. Create one above.</p>
			{:else}
				{#each lists.data.active as list (list.id)}
					<ShoppingListCard
						{list}
						otherLists={lists.data.active.filter((l) => l.id !== list.id).map((l) => ({ id: l.id, name: l.name }))}
						knownItems={known.data ?? []}
					/>
				{/each}
			{/if}

			{#if lists.data.finalized.length > 0}
				<details class="rounded-lg border border-slate-800 p-3">
					<summary class="cursor-pointer text-sm text-slate-400">
						Finalized ({lists.data.finalized.length})
					</summary>
					<div class="mt-3 space-y-3">
						{#each lists.data.finalized as list (list.id)}
							<ShoppingListCard
								{list}
								otherLists={lists.data.active.map((l) => ({ id: l.id, name: l.name }))}
								knownItems={known.data ?? []}
							/>
						{/each}
					</div>
				</details>
			{/if}
		{/if}
		{#if known.isError}
			<p class="text-xs text-red-400">Could not load suggestions: {known.error.message}</p>
		{/if}
	</div>
</main>
