import { notificationsApi } from './api';

/** True when this browser can do Web Push at all (needs HTTPS and, on iPhone, an app added to the home screen). */
export function pushSupported(): boolean {
	return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
}

function urlBase64ToBytes(base64Url: string): Uint8Array {
	const padded = base64Url.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (base64Url.length % 4)) % 4);
	const raw = atob(padded);
	return Uint8Array.from(raw, (c) => c.charCodeAt(0));
}

function bytesToUrlBase64(bytes: ArrayBuffer): string {
	return btoa(String.fromCharCode(...new Uint8Array(bytes)))
		.replace(/\+/g, '-')
		.replace(/\//g, '_')
		.replace(/=+$/, '');
}

/** The service worker is not active in dev mode, so waiting for it would hang forever. */
async function registration(): Promise<ServiceWorkerRegistration> {
	const timeout = new Promise<never>((_, reject) =>
		setTimeout(
			() =>
				reject(
					new Error(
						'The service worker is not active. Push works in the installed app over HTTPS (or after "pnpm build && pnpm preview"), not in dev mode.'
					)
				),
			4000
		)
	);
	return Promise.race([navigator.serviceWorker.ready, timeout]);
}

/** This browser's current push subscription, or null. */
export async function currentSubscription(): Promise<PushSubscription | null> {
	if (!pushSupported()) return null;
	try {
		return await (await registration()).pushManager.getSubscription();
	} catch {
		return null;
	}
}

/** Asks for permission, subscribes this browser and registers it with the server. */
export async function enablePush(vapidPublicKey: string): Promise<void> {
	if (!pushSupported()) throw new Error('This browser does not support push notifications.');

	const permission = await Notification.requestPermission();
	if (permission !== 'granted') {
		throw new Error('Notifications are blocked. Allow them for this site in the browser settings and try again.');
	}

	const reg = await registration();

	// A subscription is permanently bound to the server key it was created with. Reusing an old one after the
	// server's VAPID keys changed is exactly the "crypto-key/permission denied" error from the push service, so
	// only ever reuse it when it was made with today's key; otherwise drop it and subscribe fresh.
	const existing = await reg.pushManager.getSubscription();
	if (existing) {
		const existingKey = existing.options.applicationServerKey
			? bytesToUrlBase64(existing.options.applicationServerKey)
			: null;
		if (existingKey !== vapidPublicKey) await existing.unsubscribe();
	}

	const subscription =
		(await reg.pushManager.getSubscription()) ??
		(await reg.pushManager.subscribe({
			userVisibleOnly: true,
			applicationServerKey: urlBase64ToBytes(vapidPublicKey) as BufferSource
		}));

	const json = subscription.toJSON();
	if (!json.endpoint || !json.keys?.p256dh || !json.keys?.auth) {
		throw new Error('The browser returned an incomplete push subscription.');
	}
	await notificationsApi.subscribe({
		endpoint: json.endpoint,
		p256dh: json.keys.p256dh,
		auth: json.keys.auth,
		userAgent: navigator.userAgent
	});
}

/** Stops push in this browser (the server-side device entry is removed separately). */
export async function disablePushInBrowser(): Promise<void> {
	const subscription = await currentSubscription();
	await subscription?.unsubscribe();
}
