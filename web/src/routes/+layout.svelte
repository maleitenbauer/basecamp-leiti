<script lang="ts">
	import '../app.css';
	import { page } from '$app/state';
	import { goto, invalidateAll } from '$app/navigation';
	import { QueryClient, QueryClientProvider } from '@tanstack/svelte-query';
	import { logout } from '$lib/auth/auth';
	import { modules } from '$modules/registry';
	import NotificationBell from '$lib/components/NotificationBell.svelte';
	import UserMenu from '$lib/components/UserMenu.svelte';
	import { onMount } from 'svelte';
	import { pwaInfo } from 'virtual:pwa-info';

	let { children, data } = $props();
	const queryClient = new QueryClient();

	// register the service worker (push notifications and offline start both need it); the manifest link is in app.html
	onMount(async () => {
		if (pwaInfo) {
			const { registerSW } = await import('virtual:pwa-register');
			registerSW({ immediate: true });
		}
	});

	async function signOut() {
		await logout();
		queryClient.clear();
		await invalidateAll();
		await goto('/login');
	}

	const isActive = (href: string) => page.url.pathname === href || page.url.pathname.startsWith(`${href}/`);
</script>

<svelte:head>
	<title>Basecamp</title>
</svelte:head>

<QueryClientProvider client={queryClient}>
	<div class="min-h-screen bg-slate-900 text-slate-100">
		{#if data.user}
			<!-- no backdrop blur here: it would turn this bar into the containing block of the fixed dropdowns -->
			<header class="sticky top-0 z-30 border-b border-slate-800 bg-slate-900">
				<div class="h-0.5 bg-gradient-to-r from-brand-600 via-brand-400 to-transparent"></div>

				<!-- top action bar: brand on the left, notifications and account on the right -->
				<div class="mx-auto flex h-12 max-w-4xl items-center gap-2 px-3">
					<a href="/" class="mr-auto flex items-center gap-2 text-lg font-semibold">
						<img src="/logo.svg" alt="" width="28" height="28" class="h-7 w-7" />
						Basecamp
					</a>
					<NotificationBell />
					<UserMenu user={data.user} onSignOut={signOut} />
				</div>

				<!-- module navigation -->
				<nav aria-label="Modules" class="mx-auto flex max-w-4xl gap-1 overflow-x-auto px-3 pb-2 text-sm">
					{#each modules as m (m.id)}
						<a
							href={m.href}
							aria-current={isActive(m.href) ? 'page' : undefined}
							class="rounded-md px-3 py-1.5 whitespace-nowrap {isActive(m.href)
								? 'bg-slate-800 text-white'
								: 'text-slate-400 hover:bg-slate-800 hover:text-white'}"
						>
							{m.icon} {m.name}
						</a>
					{/each}
				</nav>
			</header>
		{/if}
		{@render children()}
	</div>
</QueryClientProvider>
