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

<div
	class="min-h-screen"
	style="background-image: radial-gradient(40rem 22rem at 50% -5%, rgb(249 115 22 / 0.20), transparent)"
>
<main class="mx-auto flex min-h-screen max-w-sm flex-col justify-center p-6">
	<img src="/logo.svg" alt="" width="96" height="96" class="mx-auto h-24 w-24" />
	<h1 class="mt-4 text-center text-3xl font-semibold">Basecamp</h1>
	<p class="mt-1 text-center text-slate-400">Sign in to continue.</p>

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
			class="w-full rounded-md bg-brand-500 text-slate-950 px-4 py-2 font-medium hover:bg-brand-400 disabled:opacity-50"
		>
			{busy ? 'Signing in…' : 'Sign in'}
		</button>
	</form>
</main>
</div>
