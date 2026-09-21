<script lang="ts">
	import { createMutation, useQueryClient } from '@tanstack/svelte-query';
	import { shiftDate } from '$lib/dates';
	import { todosApi } from './api';
	import { dueBadge, type Todo } from './types';

	let { todo, now }: { todo: Todo; now: string } = $props();

	const queryClient = useQueryClient();
	const refresh = () => queryClient.invalidateQueries({ queryKey: ['logbook', 'todos'] });

	let editing = $state(false);
	let title = $state('');
	let dueDate = $state('');
	let note = $state('');

	function startEdit() {
		title = todo.title;
		dueDate = todo.dueDate ?? '';
		note = todo.note ?? '';
		editing = true;
	}

	const toggle = createMutation(() => ({
		mutationFn: (done: boolean) => todosApi.update(todo.id, { done }),
		onSuccess: refresh
	}));

	const save = createMutation(() => ({
		mutationFn: () =>
			todosApi.update(todo.id, {
				title: title.trim(),
				dueDate: dueDate || undefined,
				clearDueDate: dueDate ? undefined : true,
				note // an empty string removes the note
			}),
		onSuccess: () => {
			editing = false;
			refresh();
		}
	}));

	const remove = createMutation(() => ({
		mutationFn: () => todosApi.remove(todo.id),
		onSuccess: refresh
	}));

	function confirmDelete() {
		if (confirm(`Delete "${todo.title}"?`)) remove.mutate();
	}
</script>

<li
	class="rounded-lg border border-slate-700 bg-slate-800/40 p-3 {todo.done ? 'opacity-70' : ''}"
>
	{#if editing}
		<form
			onsubmit={(e) => {
				e.preventDefault();
				if (title.trim()) save.mutate();
			}}
			class="space-y-2"
		>
			<input
				bind:value={title}
				maxlength="300"
				required
				aria-label="Title"
				class="w-full rounded-md bg-slate-800 px-3 py-2 text-sm"
			/>
			<div class="flex flex-wrap items-center gap-2">
				<input
					type="date"
					bind:value={dueDate}
					aria-label="Deadline"
					class="rounded-md bg-slate-800 px-2 py-1.5 text-sm"
				/>
				<button type="button" onclick={() => (dueDate = now)} class="text-xs text-slate-400 hover:text-white">
					Today
				</button>
				<button
					type="button"
					onclick={() => (dueDate = shiftDate(now, 1))}
					class="text-xs text-slate-400 hover:text-white"
				>
					Tomorrow
				</button>
				{#if dueDate}
					<button type="button" onclick={() => (dueDate = '')} class="text-xs text-slate-400 hover:text-white">
						No deadline
					</button>
				{/if}
			</div>
			<textarea
				bind:value={note}
				rows="3"
				maxlength="4000"
				placeholder="Note (optional)"
				class="w-full rounded-md bg-slate-800 px-3 py-2 text-sm"
			></textarea>
			<div class="flex items-center gap-3">
				<button
					type="submit"
					disabled={save.isPending}
					class="rounded-md bg-brand-500 px-3 py-1.5 text-sm font-medium text-slate-950 hover:bg-brand-400 disabled:opacity-50"
				>
					Save
				</button>
				<button type="button" onclick={() => (editing = false)} class="text-sm text-slate-400 hover:text-white">
					Cancel
				</button>
				<button type="button" onclick={confirmDelete} class="ml-auto text-sm text-red-400 hover:text-red-300">
					Delete
				</button>
			</div>
			{#if save.isError}<p class="text-sm text-red-400">{save.error.message}</p>{/if}
		</form>
	{:else}
		<div class="flex items-start gap-3">
			<input
				type="checkbox"
				checked={todo.done}
				onchange={(e) => toggle.mutate(e.currentTarget.checked)}
				class="mt-1 h-5 w-5"
				aria-label={todo.done ? `Reopen: ${todo.title}` : `Done: ${todo.title}`}
			/>
			<div class="min-w-0 flex-1">
				<div class="flex flex-wrap items-center gap-2">
					<span class="font-medium break-words {todo.done ? 'text-slate-400 line-through' : ''}">
						{todo.title}
					</span>
					{#if todo.dueDate && !todo.done}
						{@const badge = dueBadge(todo.dueDate, now)}
						<span class="rounded px-1.5 py-0.5 text-xs font-medium {badge.tone}">{badge.text}</span>
					{:else if todo.done}
						<span class="text-xs text-slate-500">
							done {todo.doneAt ? new Date(todo.doneAt).toLocaleDateString() : ''}
						</span>
					{/if}
				</div>
				{#if todo.note}
					<p class="mt-1 text-sm whitespace-pre-line text-slate-400">{todo.note}</p>
				{/if}
			</div>
			<button onclick={startEdit} class="text-sm text-slate-400 hover:text-white">Edit</button>
		</div>
		{#if toggle.isError}<p class="mt-1 text-sm text-red-400">{toggle.error.message}</p>{/if}
	{/if}
</li>
