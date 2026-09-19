<script lang="ts">
	import { goto, invalidateAll } from '$app/navigation';
	import { login } from '$lib/auth/auth';

	let username = $state('');
	let password = $state('');
	let error = $state('');
	let busy = $state(false);

	async function submit(e: SubmitEvent) {
		e.preventDefault();
		busy = true;
		error = '';
		try {
			await login(username.trim(), password);
			password = '';
			await invalidateAll();
			await goto('/');
		} catch (err) {
			error = err instanceof Error ? err.message : 'Login failed';
		} finally {
			busy = false;
		}
	}
</script>

<main class="mx-auto flex min-h-screen max-w-sm flex-col justify-center p-6">
	<h1 class="text-3xl font-semibold">Basecamp</h1>
	<p class="mt-1 text-slate-400">Sign in to continue.</p>

	<form onsubmit={submit} class="mt-6 space-y-3">
		<input
			bind:value={username}
			name="username"
			autocomplete="username"
			placeholder="Username"
			required
			class="w-full rounded-md bg-slate-800 px-3 py-2"
		/>
		<input
			bind:value={password}
			name="password"
			type="password"
			autocomplete="current-password"
			placeholder="Password"
			required
			class="w-full rounded-md bg-slate-800 px-3 py-2"
		/>
		{#if error}
			<p class="text-sm text-red-400" role="alert">{error}</p>
		{/if}
		<button
			type="submit"
			disabled={busy}
			class="w-full rounded-md bg-emerald-600 px-4 py-2 font-medium hover:bg-emerald-500 disabled:opacity-50"
		>
			{busy ? 'Signing in…' : 'Sign in'}
		</button>
	</form>
</main>
