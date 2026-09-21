<script lang="ts">
	import type { Me } from '$lib/auth/auth';

	let { user, onSignOut }: { user: Me; onSignOut: () => void } = $props();

	let open = $state(false);
	const close = () => (open = false);
</script>

<div class="relative">
	<button
		onclick={() => (open = !open)}
		aria-label="Account menu"
		aria-expanded={open}
		class="flex h-9 w-9 items-center justify-center rounded-full bg-brand-500 text-sm font-bold text-slate-950 hover:bg-brand-400"
	>
		{user.username.charAt(0).toUpperCase()}
	</button>

	{#if open}
		<!-- click-away layer -->
		<button
			class="fixed inset-0 z-10 cursor-default"
			aria-label="Close menu"
			tabindex="-1"
			onclick={close}
		></button>
		<div
			class="fixed top-14 right-3 z-20 w-56 rounded-lg border border-slate-700 bg-slate-900 py-1 shadow-xl sm:absolute sm:top-full sm:right-0 sm:mt-2"
		>
			<div class="border-b border-slate-800 px-3 py-2">
				<div class="truncate text-sm font-medium">{user.username}</div>
				<div class="text-xs text-slate-500">{user.role === 'ADMIN' ? 'Administrator' : 'User'}</div>
			</div>
			<a href="/account" onclick={close} class="block px-3 py-2 text-sm hover:bg-slate-800">Account</a>
			<a href="/settings/notifications" onclick={close} class="block px-3 py-2 text-sm hover:bg-slate-800">
				Notification settings
			</a>
			{#if user.role === 'ADMIN'}
				<a href="/admin/users" onclick={close} class="block px-3 py-2 text-sm hover:bg-slate-800">Users</a>
			{/if}
			<button
				onclick={() => {
					close();
					onSignOut();
				}}
				class="block w-full border-t border-slate-800 px-3 py-2 text-left text-sm text-slate-300 hover:bg-slate-800"
			>
				Sign out
			</button>
		</div>
	{/if}
</div>
