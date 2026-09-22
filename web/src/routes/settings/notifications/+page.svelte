<script lang="ts">
	import { createMutation, createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { browserTimezone, notificationsApi, type TestResult } from '$lib/notifications/api';
	import { currentSubscription, disablePushInBrowser, enablePush, pushSupported } from '$lib/notifications/push';

	const queryClient = useQueryClient();
	const refresh = () => queryClient.invalidateQueries({ queryKey: ['notification-config'] });

	const config = createQuery(() => ({
		queryKey: ['notification-config'],
		queryFn: () => notificationsApi.config()
	}));

	// which of the listed devices is this browser?
	let thisEndpoint = $state<string | null>(null);
	$effect(() => {
		config.data; // re-check after every change
		currentSubscription().then((s) => (thisEndpoint = s?.endpoint ?? null));
	});

	const supported = pushSupported();
	const permission = () => (supported ? Notification.permission : 'denied');
	const thisDeviceEnabled = $derived(
		!!thisEndpoint && !!config.data?.devices.some((d) => d.endpoint === thisEndpoint)
	);

	let message = $state('');
	let error = $state('');

	const enable = createMutation(() => ({
		mutationFn: () => enablePush(config.data!.vapidPublicKey!),
		onMutate: () => ((message = ''), (error = '')),
		onSuccess: () => {
			message = 'Push is enabled on this device.';
			refresh();
		},
		onError: (e: Error) => (error = e.message)
	}));

	const remove = createMutation(() => ({
		mutationFn: async (id: number) => {
			const device = config.data?.devices.find((d) => d.id === id);
			await notificationsApi.unsubscribe(id);
			if (device && device.endpoint === thisEndpoint) await disablePushInBrowser();
		},
		onSuccess: refresh,
		onError: (e: Error) => (error = e.message)
	}));

	// what happened to each device on the last test, so a missing push can be diagnosed instead of guessed at
	let testResult = $state<TestResult | null>(null);

	const test = createMutation(() => ({
		mutationFn: () => notificationsApi.test(),
		onMutate: () => ((message = ''), (error = ''), (testResult = null)),
		onSuccess: (result) => {
			testResult = result;
			queryClient.invalidateQueries({ queryKey: ['notifications'] });
			refresh();
		},
		onError: (e: Error) => (error = e.message)
	}));

	const setTimezone = createMutation(() => ({
		mutationFn: () => notificationsApi.setTimezone(browserTimezone()),
		onSuccess: refresh,
		onError: (e: Error) => (error = e.message)
	}));
</script>

<main class="mx-auto max-w-2xl space-y-6 p-4 sm:p-6">
	<div>
		<h1 class="text-2xl font-semibold">Notifications</h1>
		<p class="mt-1 text-sm text-slate-400">
			Reminders always show up in the bell. Turn on push to also get them on your phone or PC when the app is closed.
		</p>
	</div>

	{#if config.isPending}
		<p class="text-slate-400">Loading…</p>
	{:else if config.isError}
		<p class="text-red-400">Could not load settings: {config.error.message}</p>
	{:else}
		<section class="space-y-3 rounded-lg border border-slate-700 p-4">
			<h2 class="text-sm font-medium tracking-wide text-slate-400 uppercase">Push on this device</h2>

			{#if config.data.pushProblem}
				<div class="space-y-1 rounded-md border border-red-900 bg-red-950/30 p-3 text-sm" role="alert">
					<p class="font-medium text-red-300">The server's push keys are set up wrong.</p>
					<p class="text-red-200">{config.data.pushProblem}</p>
					<p class="text-xs text-slate-400">
						Fix the three VAPID_ values in the server's .env and redeploy. Devices that were enabled with the old keys
						must be removed below and enabled again afterwards.
					</p>
				</div>
			{:else if !config.data.pushAvailable}
				<p class="text-sm text-amber-400">
					Push is not set up on the server yet (the VAPID keys are missing). The bell still works.
				</p>
			{:else if !supported}
				<p class="text-sm text-amber-400">
					This browser can't do push notifications. On an iPhone, add the app to the home screen first (iOS 16.4 or newer).
				</p>
			{:else if permission() === 'denied'}
				<p class="text-sm text-amber-400">
					Notifications are blocked for this site. Allow them in the browser's site settings, then reload this page.
				</p>
			{:else if thisDeviceEnabled}
				<p class="text-sm text-emerald-400">Enabled on this device.</p>
			{:else}
				<button
					onclick={() => enable.mutate()}
					disabled={enable.isPending}
					class="rounded-md bg-brand-500 px-4 py-2 text-sm font-medium text-slate-950 hover:bg-brand-400 disabled:opacity-50"
				>
					{enable.isPending ? 'Enabling…' : 'Enable on this device'}
				</button>
			{/if}

			<div class="flex flex-wrap items-center gap-3">
				<button
					onclick={() => test.mutate()}
					disabled={test.isPending}
					class="rounded-md bg-slate-800 px-3 py-1.5 text-sm hover:bg-slate-700 disabled:opacity-50"
				>
					Send a test notification
				</button>
				{#if message}<span class="text-sm text-emerald-400">{message}</span>{/if}
				{#if error}<span class="text-sm text-red-400" role="alert">{error}</span>{/if}
			</div>

			{#if testResult}
				<div class="space-y-1 rounded-md bg-slate-800/60 p-3 text-sm" role="status">
					<p class="text-emerald-400">✓ Added to the bell.</p>
					{#if !testResult.pushAvailable}
						<p class="text-amber-400">
							✗ Push: the server has no VAPID keys (or they are invalid), so nothing was pushed.
						</p>
					{:else if testResult.deviceCount === 0}
						<p class="text-amber-400">
							✗ Push: no device has push enabled yet. Press "Enable on this device" first.
						</p>
					{:else}
						{#each testResult.deliveries as delivery, i (i)}
							<p class={delivery.delivered ? 'text-emerald-400' : 'text-red-400'}>
								{delivery.delivered ? '✓' : '✗'}
								{delivery.device}: {delivery.detail}
							</p>
						{/each}
						{#if testResult.deliveries.some((delivery) => delivery.delivered)}
							<p class="pt-1 text-xs text-slate-500">
								"Accepted" means the push service took the message. If it still doesn't appear, check that
								notifications are switched on for your browser in the system settings (Windows: Settings →
								System → Notifications, with Focus assist off; Android: the browser's or app's notification
								settings), and that the browser or installed app is allowed to run in the background.
							</p>
						{/if}
					{/if}
				</div>
			{/if}
		</section>

		<section class="space-y-2 rounded-lg border border-slate-700 p-4">
			<h2 class="text-sm font-medium tracking-wide text-slate-400 uppercase">Your devices</h2>
			{#if config.data.devices.length === 0}
				<p class="text-sm text-slate-500">No device has push enabled yet.</p>
			{:else}
				<ul class="divide-y divide-slate-800 text-sm">
					{#each config.data.devices as d (d.id)}
						<li class="flex items-center gap-3 py-2">
							<span class="min-w-0 flex-1">
								{d.label}
								{#if d.endpoint === thisEndpoint}
									<span class="ml-1 rounded bg-brand-500 px-1.5 py-0.5 text-[10px] font-semibold text-slate-950">
										this device
									</span>
								{/if}
								<span class="block text-xs text-slate-500">
									added {new Date(d.createdAt).toLocaleDateString()}
								</span>
							</span>
							<button
								onclick={() => confirm(`Remove ${d.label}?`) && remove.mutate(d.id)}
								class="text-slate-400 hover:text-red-400"
							>
								Remove
							</button>
						</li>
					{/each}
				</ul>
			{/if}
		</section>

		<section class="space-y-2 rounded-lg border border-slate-700 p-4">
			<h2 class="text-sm font-medium tracking-wide text-slate-400 uppercase">Time zone</h2>
			<p class="text-sm">
				Reminders use <span class="font-medium">{config.data.timezone}</span>.
			</p>
			{#if config.data.timezone !== browserTimezone()}
				<p class="text-sm text-slate-400">
					This browser is in <span class="font-medium">{browserTimezone()}</span>.
				</p>
				<button
					onclick={() => setTimezone.mutate()}
					class="rounded-md bg-slate-800 px-3 py-1.5 text-sm hover:bg-slate-700"
				>
					Use {browserTimezone()}
				</button>
			{/if}
		</section>
	{/if}
</main>
