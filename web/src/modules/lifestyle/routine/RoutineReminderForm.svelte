<script lang="ts">
	import { createMutation, useQueryClient } from '@tanstack/svelte-query';
	import { untrack } from 'svelte';
	import { browserTimezone } from '$lib/notifications/api';
	import { routineApi } from './api';
	import type { ReminderSettings } from './types';

	let { settings }: { settings: ReminderSettings } = $props();

	const queryClient = useQueryClient();

	// the form starts from the saved values on purpose; the parent re-creates it (via {#key}) after a save
	let enabled = $state(untrack(() => settings.enabled));
	let remindAt = $state(untrack(() => settings.remindAt));

	let saved = $state(false);
	let sendNowMessage = $state('');

	const save = createMutation(() => ({
		mutationFn: () =>
			routineApi.saveReminder({
				enabled,
				remindAt,
				timezone: browserTimezone() // reminders follow the browser's time zone
			}),
		onSuccess: () => {
			saved = true;
			queryClient.invalidateQueries({ queryKey: ['lifestyle', 'routine', 'reminder'] });
		}
	}));

	const sendNow = createMutation(() => ({
		mutationFn: () => routineApi.sendNow(),
		onMutate: () => (sendNowMessage = ''),
		onSuccess: (result) => {
			sendNowMessage = result.sent ? `Sent: ${result.message}` : result.message;
			queryClient.invalidateQueries({ queryKey: ['notifications'] });
		}
	}));
</script>

<form
	onsubmit={(e) => {
		e.preventDefault();
		saved = false;
		save.mutate();
	}}
	class="mt-3 space-y-3"
>
	<label class="flex items-center gap-3">
		<input type="checkbox" bind:checked={enabled} class="h-5 w-5" />
		<span class="text-sm font-medium">Remind me of what's still open</span>
	</label>

	<div class="flex flex-wrap items-center gap-3 {enabled ? '' : 'opacity-50'}">
		<label class="flex items-center gap-2 text-sm">
			Every day at
			<input
				type="time"
				bind:value={remindAt}
				required
				disabled={!enabled}
				class="rounded-md bg-slate-800 px-2 py-1.5 text-sm"
			/>
		</label>
		<span class="text-xs text-slate-500">({browserTimezone()})</span>
	</div>

	<div class="flex flex-wrap items-center gap-3">
		<button
			type="submit"
			disabled={save.isPending}
			class="rounded-md bg-brand-500 px-4 py-2 text-sm font-medium text-slate-950 hover:bg-brand-400 disabled:opacity-50"
		>
			Save
		</button>
		<button
			type="button"
			onclick={() => sendNow.mutate()}
			disabled={sendNow.isPending}
			class="rounded-md bg-slate-800 px-3 py-2 text-sm hover:bg-slate-700 disabled:opacity-50"
		>
			Send a reminder now
		</button>
		{#if saved}<span class="text-sm text-emerald-400">Saved.</span>{/if}
		{#if save.isError}<span class="text-sm text-red-400">{save.error.message}</span>{/if}
	</div>
	{#if sendNowMessage}<p class="text-sm text-slate-300">{sendNowMessage}</p>{/if}
	{#if sendNow.isError}<p class="text-sm text-red-400">{sendNow.error.message}</p>{/if}

	<p class="text-xs text-slate-500">
		You only get a reminder when something is still open at that time. It shows up in the bell; to also get it on
		your phone or PC when the app is closed, enable push under
		<a href="/settings/notifications" class="text-brand-400 hover:text-brand-300">Notification settings</a>.
	</p>
</form>
