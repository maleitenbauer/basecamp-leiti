<script lang="ts">
	// Small inline picker for "move to another list": pick an existing active list, or type a new name.
	let {
		otherLists,
		onMove,
		label = 'Move'
	}: {
		otherLists: { id: number; name: string }[];
		onMove: (target: { targetListId?: number; newListName?: string }) => void;
		label?: string;
	} = $props();

	let open = $state(false);
	let newName = $state('');

	function pick(id: number) {
		open = false;
		onMove({ targetListId: id });
	}

	function createAndMove(e: SubmitEvent) {
		e.preventDefault();
		if (!newName.trim()) return;
		open = false;
		onMove({ newListName: newName.trim() });
		newName = '';
	}
</script>

<div class="relative inline-block">
	<button
		type="button"
		onclick={() => (open = !open)}
		class="text-xs text-slate-400 hover:text-white"
		aria-expanded={open}
	>
		{label}
	</button>
	{#if open}
		<button
			class="fixed inset-0 z-10 cursor-default"
			aria-label="Close"
			tabindex="-1"
			onclick={() => (open = false)}
		></button>
		<div
			class="absolute right-0 z-20 mt-1 w-56 rounded-lg border border-slate-700 bg-slate-900 p-2 text-sm shadow-xl"
		>
			{#if otherLists.length > 0}
				<p class="px-1 pb-1 text-xs text-slate-500">To an existing list</p>
				<ul class="mb-2 max-h-40 overflow-y-auto">
					{#each otherLists as l (l.id)}
						<li>
							<button
								type="button"
								onclick={() => pick(l.id)}
								class="block w-full truncate rounded px-2 py-1.5 text-left hover:bg-slate-800"
							>
								{l.name}
							</button>
						</li>
					{/each}
				</ul>
			{/if}
			<form onsubmit={createAndMove} class="flex gap-1 border-t border-slate-800 pt-2">
				<input
					bind:value={newName}
					placeholder="New list…"
					maxlength="200"
					class="min-w-0 flex-1 rounded-md bg-slate-800 px-2 py-1 text-xs"
				/>
				<button type="submit" class="rounded-md bg-brand-500 px-2 py-1 text-xs font-medium text-slate-950">
					Go
				</button>
			</form>
		</div>
	{/if}
</div>
