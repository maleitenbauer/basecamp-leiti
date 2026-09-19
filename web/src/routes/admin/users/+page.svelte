<script lang="ts">
	import { createMutation, createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { http } from '$lib/api/http';

	interface User {
		id: number;
		username: string;
		role: 'ADMIN' | 'USER';
		enabled: boolean;
		locked: boolean;
		lastLoginAt: string | null;
		createdAt: string;
	}

	let { data } = $props();
	const queryClient = useQueryClient();
	const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });

	const users = createQuery(() => ({
		queryKey: ['admin', 'users'],
		queryFn: () => http<User[]>('/api/admin/users')
	}));

	let username = $state('');
	let password = $state('');
	let role = $state<'USER' | 'ADMIN'>('USER');

	const create = createMutation(() => ({
		mutationFn: () =>
			http<User>('/api/admin/users', {
				method: 'POST',
				body: JSON.stringify({ username: username.trim(), password, role })
			}),
		onSuccess: () => {
			username = '';
			password = '';
			refresh();
		}
	}));

	const update = createMutation(() => ({
		mutationFn: (v: { id: number; body: Partial<{ enabled: boolean; role: string; newPassword: string }> }) =>
			http<User>(`/api/admin/users/${v.id}`, { method: 'PATCH', body: JSON.stringify(v.body) }),
		onSuccess: refresh
	}));

	function resetPassword(u: User) {
		const newPassword = prompt(`New password for ${u.username} (12–72 characters):`);
		if (newPassword) update.mutate({ id: u.id, body: { newPassword } });
	}
</script>

<main class="mx-auto max-w-4xl p-4 sm:p-6">
	<h1 class="text-2xl font-semibold">Users</h1>

	<form
		onsubmit={(e) => {
			e.preventDefault();
			create.mutate();
		}}
		class="mt-4 flex flex-wrap gap-2 rounded-lg border border-slate-700 p-3"
	>
		<input
			bind:value={username}
			placeholder="Username"
			autocomplete="off"
			minlength="3"
			maxlength="64"
			required
			class="min-w-32 flex-1 rounded-md bg-slate-800 px-3 py-2 text-sm"
		/>
		<input
			bind:value={password}
			type="password"
			placeholder="Password (12–72)"
			autocomplete="new-password"
			minlength="12"
			maxlength="72"
			required
			class="min-w-40 flex-1 rounded-md bg-slate-800 px-3 py-2 text-sm"
		/>
		<select bind:value={role} class="rounded-md bg-slate-800 px-2 py-2 text-sm">
			<option value="USER">User</option>
			<option value="ADMIN">Admin</option>
		</select>
		<button
			type="submit"
			disabled={create.isPending}
			class="rounded-md bg-brand-500 text-slate-950 px-4 py-2 text-sm font-medium hover:bg-brand-400 disabled:opacity-50"
		>
			Add user
		</button>
		{#if create.isError}<p class="w-full text-sm text-red-400">{create.error.message}</p>{/if}
	</form>

	<div class="mt-4 space-y-2">
		{#if users.isPending}
			<p class="text-slate-400">Loading…</p>
		{:else if users.isError}
			<p class="text-red-400">Could not load users: {users.error.message}</p>
		{:else}
			{#each users.data as u (u.id)}
				<div class="flex flex-wrap items-center gap-3 rounded-lg border border-slate-700 p-3 text-sm">
					<div class="min-w-0 flex-1">
						<div class="font-medium">
							{u.username}
							<span class="text-xs text-slate-400">{u.role}</span>
							{#if !u.enabled}<span class="text-xs text-amber-400">disabled</span>{/if}
							{#if u.locked}<span class="text-xs text-red-400">locked</span>{/if}
						</div>
						<div class="text-xs text-slate-500">
							Last login: {u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : 'never'}
						</div>
					</div>
					<button onclick={() => resetPassword(u)} class="text-slate-400 hover:text-white">
						Reset password
					</button>
					{#if u.id !== data.user?.id}
						<button
							onclick={() =>
								update.mutate({ id: u.id, body: { role: u.role === 'ADMIN' ? 'USER' : 'ADMIN' } })}
							class="text-slate-400 hover:text-white"
						>
							Make {u.role === 'ADMIN' ? 'user' : 'admin'}
						</button>
						<button
							onclick={() => update.mutate({ id: u.id, body: { enabled: !u.enabled } })}
							class="{u.enabled ? 'text-red-400 hover:text-red-300' : 'text-emerald-400 hover:text-emerald-300'}"
						>
							{u.enabled ? 'Disable' : 'Enable'}
						</button>
					{/if}
				</div>
			{/each}
		{/if}
		{#if update.isError}<p class="text-sm text-red-400">{update.error.message}</p>{/if}
	</div>
</main>
