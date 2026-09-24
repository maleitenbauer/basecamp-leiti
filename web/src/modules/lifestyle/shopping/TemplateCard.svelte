<script lang="ts">
	import { createMutation, useQueryClient } from '@tanstack/svelte-query';
	import { shoppingApi } from './api';
	import { itemLabel, type Template } from './types';

	let { template }: { template: Template } = $props();

	const queryClient = useQueryClient();
	const refreshTemplates = () => queryClient.invalidateQueries({ queryKey: ['lifestyle', 'shopping', 'templates'] });

	let expanded = $state(false);
	let newItemName = $state('');
	let useAsName = $state('');
	let usingAs = $state(false);

	const use = createMutation(() => ({
		mutationFn: (name: string) => shoppingApi.useTemplate(template.id, { name: name || undefined }),
		onSuccess: () => {
			usingAs = false;
			queryClient.invalidateQueries({ queryKey: ['lifestyle', 'shopping', 'lists'] });
			queryClient.invalidateQueries({ queryKey: ['lifestyle', 'shopping', 'known-items'] });
		}
	}));

	const addItem = createMutation(() => ({
		mutationFn: (name: string) => shoppingApi.addTemplateItem(template.id, { name }),
		onSuccess: () => {
			newItemName = '';
			refreshTemplates();
		}
	}));

	const removeItem = createMutation(() => ({
		mutationFn: (id: number) => shoppingApi.removeTemplateItem(id),
		onSuccess: refreshTemplates
	}));

	const remove = createMutation(() => ({
		mutationFn: () => shoppingApi.deleteTemplate(template.id),
		onSuccess: refreshTemplates
	}));

	function confirmDelete() {
		if (confirm(`Delete the template "${template.name}"?`)) remove.mutate();
	}
</script>

<article class="rounded-lg border border-slate-700 bg-slate-800/40">
	<div class="flex flex-wrap items-center gap-2 p-3">
		<button onclick={() => (expanded = !expanded)} class="min-w-0 flex-1 truncate text-left font-medium">
			{template.name}
			<span class="ml-2 text-xs font-normal text-slate-500">{template.items.length} items</span>
		</button>

		{#if usingAs}
			<form
				onsubmit={(e) => {
					e.preventDefault();
					use.mutate(useAsName.trim());
				}}
				class="flex gap-1"
			>
				<input
					bind:value={useAsName}
					placeholder={template.name}
					maxlength="200"
					class="w-32 rounded-md bg-slate-800 px-2 py-1 text-sm"
				/>
				<button
					type="submit"
					disabled={use.isPending}
					class="rounded-md bg-brand-500 px-2 py-1 text-xs font-medium text-slate-950"
				>
					Go
				</button>
			</form>
		{:else}
			<button
				onclick={() => {
					useAsName = '';
					usingAs = true;
				}}
				disabled={use.isPending}
				class="rounded-md bg-brand-500 px-3 py-1.5 text-xs font-medium text-slate-950 hover:bg-brand-400"
			>
				Use → new list
			</button>
		{/if}
		<button onclick={confirmDelete} class="text-xs text-red-400 hover:text-red-300">Delete</button>
	</div>
	{#if use.isError}<p class="px-3 pb-2 text-xs text-red-400">{use.error.message}</p>{/if}

	{#if expanded}
		<div class="space-y-2 border-t border-slate-700 p-3">
			<form
				onsubmit={(e) => {
					e.preventDefault();
					if (newItemName.trim()) addItem.mutate(newItemName.trim());
				}}
				class="flex gap-2"
			>
				<input
					bind:value={newItemName}
					placeholder="Add item…"
					maxlength="200"
					class="min-w-0 flex-1 rounded-md bg-slate-800 px-3 py-1.5 text-sm"
				/>
				<button
					type="submit"
					disabled={addItem.isPending}
					class="rounded-md bg-slate-700 px-3 py-1.5 text-sm hover:bg-slate-600"
				>
					Add
				</button>
			</form>
			{#if addItem.isError}<p class="text-xs text-red-400">{addItem.error.message}</p>{/if}

			{#if template.items.length === 0}
				<p class="text-sm text-slate-500">No items yet.</p>
			{:else}
				<ul class="divide-y divide-slate-800">
					{#each template.items as item (item.id)}
						<li class="flex items-center gap-2 py-1.5 text-sm">
							<span class="min-w-0 flex-1 truncate">{item.name}</span>
							{#if itemLabel(item)}<span class="text-xs text-slate-500">{itemLabel(item)}</span>{/if}
							<button
								onclick={() => removeItem.mutate(item.id)}
								class="text-slate-500 hover:text-red-400"
								aria-label="Remove {item.name}"
							>
								✕
							</button>
						</li>
					{/each}
				</ul>
			{/if}
		</div>
	{/if}
</article>
