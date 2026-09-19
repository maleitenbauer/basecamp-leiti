<script lang="ts">
	import { http } from '$lib/api/http';

	let { data } = $props();

	let currentPassword = $state('');
	let newPassword = $state('');
	let message = $state('');
	let error = $state('');
	let busy = $state(false);

	async function submit(e: SubmitEvent) {
		e.preventDefault();
		busy = true;
		message = '';
		error = '';
		try {
			await http<void>('/api/auth/password', {
				method: 'POST',
				body: JSON.stringify({ currentPassword, newPassword })
			});
			currentPassword = '';
			newPassword = '';
			message = 'Password changed. Your other devices were signed out.';
		} catch (err) {
			error = err instanceof Error ? err.message : 'Could not change the password';
		} finally {
			busy = false;
		}
	}
</script>

<main class="mx-auto max-w-md p-6">
	<h1 class="text-2xl font-semibold">Account</h1>
	<p class="mt-1 text-slate-400">{data.user?.username} · {data.user?.role}</p>

	<form onsubmit={submit} class="mt-6 space-y-3">
		<h2 class="text-sm font-medium tracking-wide text-slate-400 uppercase">Change password</h2>
		<input
			bind:value={currentPassword}
			type="password"
			autocomplete="current-password"
			placeholder="Current password"
			required
			class="w-full rounded-md bg-slate-800 px-3 py-2"
		/>
		<input
			bind:value={newPassword}
			type="password"
			autocomplete="new-password"
			placeholder="New password (12–72 characters)"
			minlength="12"
			maxlength="72"
			required
			class="w-full rounded-md bg-slate-800 px-3 py-2"
		/>
		{#if error}<p class="text-sm text-red-400" role="alert">{error}</p>{/if}
		{#if message}<p class="text-sm text-emerald-400">{message}</p>{/if}
		<button
			type="submit"
			disabled={busy}
			class="rounded-md bg-brand-500 text-slate-950 px-4 py-2 font-medium hover:bg-brand-400 disabled:opacity-50"
		>
			Change password
		</button>
	</form>
</main>
