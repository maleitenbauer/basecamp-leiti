<script lang="ts">
	import { createMutation, useQueryClient } from '@tanstack/svelte-query';
	import AddItemForm from './AddItemForm.svelte';
	import { shoppingApi } from './api';
	import ItemRow from './ItemRow.svelte';
	import MovePicker from './MovePicker.svelte';
	import type { KnownItem, ShoppingList } from './types';

	let {
		list,
		otherLists,
		knownItems
	}: { list: ShoppingList; otherLists: { id: number; name: string }[]; knownItems: KnownItem[] } = $props();

	const queryClient = useQueryClient();
	const refresh = () => queryClient.invalidateQueries({ queryKey: ['lifestyle', 'shopping'] });

	// the rename input only exists once `renaming` is true, so focus it on mount rather than via the autofocus attribute
	function focusOnMount(node: HTMLInputElement) {
		node.focus();
	}

	let renaming = $state(false);
	let nameDraft = $state('');
	let expanded = $state(true);

	const unchecked = $derived(list.items.filter((i) => !i.checked));
	const checked = $derived(list.items.filter((i) => i.checked));
	const allChecked = $derived(list.items.length > 0 && unchecked.length === 0);

	const rename = createMutation(() => ({
		mutationFn: () => shoppingApi.updateList(list.id, { name: nameDraft.trim() }),
		onSuccess: () => {
			renaming = false;
			refresh();
		}
	}));

	const finalize = createMutation(() => ({
		mutationFn: (finalized: boolean) => shoppingApi.updateList(list.id, { finalized }),
		onSuccess: refresh
	}));

	const remove = createMutation(() => ({
		mutationFn: () => shoppingApi.deleteList(list.id),
		onSuccess: refresh
	}));

	const moveUnchecked = createMutation(() => ({
		mutationFn: (target: Parameters<typeof shoppingApi.moveUnchecked>[1]) => shoppingApi.moveUnchecked(list.id, target),
		onSuccess: refresh
	}));

	function confirmDelete() {
		if (confirm(`Delete "${list.name}" and all its items?`)) remove.mutate();
	}

	let savingTemplate = $state(false);
	let templateName = $state('');
	let templateSaved = $state(false);

	const saveTemplate = createMutation(() => ({
		mutationFn: () => shoppingApi.saveListAsTemplate(list.id, { name: templateName.trim() }),
		onSuccess: () => {
			savingTemplate = false;
			templateSaved = true;
			queryClient.invalidateQueries({ queryKey: ['lifestyle', 'shopping', 'templates'] });
		}
	}));
</script>

<article class="rounded-lg border border-slate-700 bg-slate-800/40">
	<div class="flex flex-wrap items-center gap-2 p-3">
		{#if renaming}
			<form
				onsubmit={(e) => {
					e.preventDefault();
					if (nameDraft.trim()) rename.mutate();
				}}
				class="flex min-w-0 flex-1 gap-1"
			>
				<input
					bind:value={nameDraft}
					maxlength="200"
					use:focusOnMount
					class="min-w-0 flex-1 rounded-md bg-slate-800 px-2 py-1 text-sm font-medium"
				/>
				<button type="submit" class="text-sm text-brand-400 hover:text-brand-300">Save</button>
				<button type="button" onclick={() => (renaming = false)} class="text-sm text-slate-400">Cancel</button>
			</form>
		{:else}
			<button
				onclick={() => (expanded = !expanded)}
				class="min-w-0 flex-1 truncate text-left font-medium"
				aria-expanded={expanded}
			>
				{list.name}
				<span class="ml-2 text-xs font-normal text-slate-500">
					{list.items.length - unchecked.length}/{list.items.length}
				</span>
			</button>
			<button
				onclick={() => {
					nameDraft = list.name;
					renaming = true;
				}}
				class="text-xs text-slate-500 hover:text-white"
			>
				Rename
			</button>
		{/if}

		{#if !list.finalizedAt}
			<button
				onclick={() => finalize.mutate(true)}
				disabled={finalize.isPending}
				class="rounded-md px-2 py-1 text-xs {allChecked
					? 'bg-brand-500 font-medium text-slate-950 hover:bg-brand-400'
					: 'bg-slate-800 text-slate-300 hover:bg-slate-700'}"
			>
				Finalize
			</button>
		{:else}
			<button
				onclick={() => finalize.mutate(false)}
				disabled={finalize.isPending}
				class="rounded-md bg-slate-800 px-2 py-1 text-xs text-slate-300 hover:bg-slate-700"
			>
				Reopen
			</button>
		{/if}
		<button onclick={confirmDelete} class="text-xs text-red-400 hover:text-red-300">Delete</button>
	</div>

	{#if expanded}
		<div class="space-y-3 border-t border-slate-700 p-3">
			{#if !list.finalizedAt}
				<AddItemForm listId={list.id} {knownItems} />
			{/if}

			{#if savingTemplate}
				<form
					onsubmit={(e) => {
						e.preventDefault();
						if (templateName.trim()) saveTemplate.mutate();
					}}
					class="flex gap-1"
				>
					<input
						bind:value={templateName}
						use:focusOnMount
						placeholder="Template name…"
						maxlength="200"
						class="min-w-0 flex-1 rounded-md bg-slate-800 px-2 py-1.5 text-sm"
					/>
					<button
						type="submit"
						disabled={saveTemplate.isPending}
						class="rounded-md bg-brand-500 px-3 py-1.5 text-sm font-medium text-slate-950 hover:bg-brand-400"
					>
						Save
					</button>
					<button type="button" onclick={() => (savingTemplate = false)} class="text-sm text-slate-400">
						Cancel
					</button>
				</form>
			{:else if list.items.length > 0}
				<button
					onclick={() => {
						templateName = list.name;
						templateSaved = false;
						savingTemplate = true;
					}}
					class="text-xs text-slate-400 hover:text-white"
				>
					Save as recurring template
				</button>
				{#if templateSaved}<span class="ml-2 text-xs text-emerald-400">Saved.</span>{/if}
			{/if}
			{#if saveTemplate.isError}<p class="text-xs text-red-400">{saveTemplate.error.message}</p>{/if}

			{#if list.items.length === 0}
				<p class="text-sm text-slate-500">No items yet.</p>
			{:else}
				{#if unchecked.length > 0}
					<div class="flex items-center justify-between">
						<span class="text-xs text-slate-500">{unchecked.length} to get</span>
						<MovePicker {otherLists} label="Move rest to…" onMove={(target) => moveUnchecked.mutate(target)} />
					</div>
					<ul class="divide-y divide-slate-800">
						{#each unchecked as item (item.id)}
							<ItemRow {item} {otherLists} />
						{/each}
					</ul>
				{/if}
				{#if checked.length > 0}
					<details open={unchecked.length === 0}>
						<summary class="cursor-pointer text-xs text-slate-500">Got ({checked.length})</summary>
						<ul class="mt-1 divide-y divide-slate-800">
							{#each checked as item (item.id)}
								<ItemRow {item} {otherLists} />
							{/each}
						</ul>
					</details>
				{/if}
			{/if}
			{#if moveUnchecked.isError}<p class="text-xs text-red-400">{moveUnchecked.error.message}</p>{/if}
			{#if rename.isError}<p class="text-xs text-red-400">{rename.error.message}</p>{/if}
		</div>
	{/if}
</article>
