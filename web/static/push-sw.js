// Web Push handlers. Imported by the generated service worker (see workbox.importScripts in vite.config.ts).
// The server sends JSON: { title, body, url }.

self.addEventListener('push', (event) => {
	let data = {};
	try {
		data = event.data ? event.data.json() : {};
	} catch {
		data = { title: 'Basecamp', body: event.data ? event.data.text() : '' };
	}

	const title = data.title || 'Basecamp';
	event.waitUntil(
		self.registration.showNotification(title, {
			body: data.body || '',
			icon: '/pwa-192x192.png',
			badge: '/pwa-64x64.png',
			// same-tag notifications replace each other instead of piling up
			tag: data.tag || 'basecamp',
			data: { url: data.url || '/' }
		})
	);
});

self.addEventListener('notificationclick', (event) => {
	event.notification.close();
	const target = new URL((event.notification.data && event.notification.data.url) || '/', self.location.origin).href;

	event.waitUntil(
		(async () => {
			const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
			for (const client of windows) {
				// reuse an open window of the app
				if ('focus' in client) {
					await client.focus();
					if ('navigate' in client) await client.navigate(target);
					return;
				}
			}
			await self.clients.openWindow(target);
		})()
	);
});
