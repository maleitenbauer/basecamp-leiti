<script lang="ts">
	import { createMutation, createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { notificationsApi } from '$lib/notifications/api';

	const queryClient = useQueryClient();
	let open = $state(false);

	const list = createQuery(() => ({
		queryKey: ['notifications'],
		queryFn: () => notificationsApi.list(),
		refetchInterval: 60_000,
		retry: false
	}));

	const refresh = () => queryClient.invalidateQueries({ queryKey: ['notifications'] });

	const readAll = createMutation(() => ({
		mutationFn: () => notificationsApi.readAll(),
		onSuccess: refresh
	}));

	const readOne = createMutation(() => ({
		mutationFn: (id: number) => notificationsApi.read(id),
		onSuccess: refresh
	}));

	const unread = $derived(list.data?.unread ?? 0);

	function when(iso: string): string {
		const d = new Date(iso);
		const sameDay = d.toDateString() === new Date().toDateString();
		return sameDay
			? d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
			: d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
	}
</script>

<div class="relative">
	<button
		onclick={() => (open = !open)}
		aria-label="Notifications{unread > 0 ? `, ${unread} unread` : ''}"
		aria-expanded={open}
		class="relative rounded-md bg-slate-800 px-2.5 py-1.5 hover:bg-slate-700"
	>
		🔔
		{#if unread > 0}
			<span
				class="absolute -top-1 -right-1 min-w-4 rounded-full bg-brand-500 px-1 text-center text-[10px] leading-4 font-bold text-slate-950"
			>
				{unread > 9 ? '9+' : unread}
			</span>
		{/if}
	</button>

	{#if open}
		<!-- click-away layer -->
		<button
			class="fixed inset-0 z-10 cursor-default"
			aria-label="Close notifications"
			tabindex="-1"
			onclick={() => (open = false)}
		></button>
		<div
			class="fixed inset-x-3 top-14 z-20 rounded-lg border border-slate-700 bg-slate-900 shadow-xl sm:absolute sm:inset-x-auto sm:top-full sm:right-0 sm:mt-2 sm:w-96"
		>
			<div class="flex items-center justify-between border-b border-slate-800 px-3 py-2">
				<span class="text-sm font-medium">Notifications</span>
				{#if unread > 0}
					<button onclick={() => readAll.mutate()} class="text-xs text-brand-400 hover:text-brand-300">
						Mark all read
					</button>
				{/if}
			</div>

			{#if list.isPending}
				<p class="p-3 text-sm text-slate-400">Loading…</p>
			{:else if list.isError}
				<p class="p-3 text-sm text-red-400">Could not load notifications.</p>
			{:else if list.data.items.length === 0}
				<p class="p-3 text-sm text-slate-500">Nothing yet.</p>
			{:else}
				<ul class="max-h-[60vh] divide-y divide-slate-800 overflow-y-auto">
					{#each list.data.items as n (n.id)}
						<li>
							<a
								href={n.url ?? '/'}
								onclick={() => {
									if (!n.read) readOne.mutate(n.id);
									open = false;
								}}
								class="block px-3 py-2 hover:bg-slate-800 {n.read ? 'opacity-60' : ''}"
							>
								<div class="flex items-baseline gap-2">
									{#if !n.read}<span class="h-2 w-2 shrink-0 rounded-full bg-brand-500"></span>{/if}
									<span class="min-w-0 flex-1 truncate text-sm font-medium">{n.title}</span>
									<span class="text-xs text-slate-500">{when(n.createdAt)}</span>
								</div>
								{#if n.body}
									<p class="mt-0.5 text-xs whitespace-pre-line text-slate-400">{n.body}</p>
								{/if}
							</a>
						</li>
					{/each}
				</ul>
			{/if}

			<div class="border-t border-slate-800 px-3 py-2 text-right">
				<a
					href="/settings/notifications"
					onclick={() => (open = false)}
					class="text-xs text-slate-400 hover:text-white">Notification settings</a
				>
			</div>
		</div>
	{/if}
</div>
