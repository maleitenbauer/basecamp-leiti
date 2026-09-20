<script lang="ts">
	import { createMutation, useQueryClient } from '@tanstack/svelte-query';
	import { untrack } from 'svelte';
	import { matchesApi } from './api';
	import type { MatchSettings } from './types';

	let { settings, open = false }: { settings: MatchSettings; open?: boolean } = $props();

	const queryClient = useQueryClient();

	// the form starts from the saved values on purpose; the parent re-creates it (via {#key}) after a save
	let faceitNickname = $state(untrack(() => settings.faceitNickname ?? ''));
	let steam64Id = $state(untrack(() => settings.steam64Id ?? ''));
	let saved = $state(false);

	const save = createMutation(() => ({
		mutationFn: () =>
			matchesApi.saveSettings({
				faceitNickname: faceitNickname.trim() || null,
				steam64Id: steam64Id.trim() || null
			}),
		onSuccess: () => {
			saved = true;
			queryClient.invalidateQueries({ queryKey: ['gaming', 'cs2', 'matches'] });
		}
	}));
</script>

<details {open} class="rounded-lg border border-slate-800 p-3">
	<summary class="cursor-pointer text-sm text-slate-400">Account settings</summary>
	<form
		onsubmit={(e) => {
			e.preventDefault();
			saved = false;
			save.mutate();
		}}
		class="mt-3 space-y-3"
	>
		<label class="block">
			<span class="text-sm font-medium">FACEIT nickname</span>
			<input
				bind:value={faceitNickname}
				placeholder="your FACEIT nickname"
				maxlength="64"
				disabled={!settings.faceitAvailable}
				class="mt-1 w-full rounded-md bg-slate-800 px-3 py-2 text-sm disabled:opacity-50"
			/>
			{#if !settings.faceitAvailable}
				<span class="text-xs text-amber-400">
					FACEIT is not set up on the server yet (FACEIT_API_KEY is missing).
				</span>
			{/if}
		</label>

		<label class="block">
			<span class="text-sm font-medium">Steam64 ID</span>
			<input
				bind:value={steam64Id}
				placeholder="7656119…"
				inputmode="numeric"
				maxlength="17"
				class="mt-1 w-full rounded-md bg-slate-800 px-3 py-2 text-sm"
			/>
			<span class="text-xs text-slate-500">
				17 digits. Find yours at steamid.io. Your Leetify profile must be public.
			</span>
		</label>

		<div class="flex items-center gap-3">
			<button
				type="submit"
				disabled={save.isPending}
				class="rounded-md bg-brand-500 px-4 py-2 text-sm font-medium text-slate-950 hover:bg-brand-400 disabled:opacity-50"
			>
				Save
			</button>
			{#if saved}<span class="text-sm text-emerald-400">Saved.</span>{/if}
			{#if save.isError}<span class="text-sm text-red-400">{save.error.message}</span>{/if}
		</div>
	</form>
</details>
