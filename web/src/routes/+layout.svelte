<script lang="ts">
	import '../app.css';
	import { goto, invalidateAll } from '$app/navigation';
	import { QueryClient, QueryClientProvider } from '@tanstack/svelte-query';
	import { logout } from '$lib/auth/auth';
	import { modules } from '$modules/registry';

	let { children, data } = $props();
	const queryClient = new QueryClient();

	async function signOut() {
		await logout();
		queryClient.clear();
		await invalidateAll();
		await goto('/login');
	}
</script>

<QueryClientProvider client={queryClient}>
	<div class="min-h-screen bg-slate-900 text-slate-100">
		{#if data.user}
			<header class="border-b border-slate-800">
				<nav class="mx-auto flex max-w-4xl items-center gap-3 p-3 text-sm">
					<a href="/" class="mr-2 text-lg font-semibold">Basecamp</a>
					<div class="mr-auto flex gap-1">
						{#each modules as m (m.id)}
							<a href={m.href} class="rounded-md px-2.5 py-1.5 text-slate-300 hover:bg-slate-800 hover:text-white">
								{m.icon} {m.name}
							</a>
						{/each}
					</div>
					{#if data.user.role === 'ADMIN'}
						<a href="/admin/users" class="text-slate-400 hover:text-white">Users</a>
					{/if}
					<a href="/account" class="text-slate-400 hover:text-white">{data.user.username}</a>
					<button onclick={signOut} class="rounded-md bg-slate-800 px-3 py-1.5 hover:bg-slate-700">
						Sign out
					</button>
				</nav>
			</header>
		{/if}
		{@render children()}
	</div>
</QueryClientProvider>
