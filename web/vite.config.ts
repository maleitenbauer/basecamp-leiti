import { sveltekit } from '@sveltejs/kit/vite';
import tailwindcss from '@tailwindcss/vite';
import { SvelteKitPWA } from '@vite-pwa/sveltekit';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [
		tailwindcss(),
		sveltekit(),
		SvelteKitPWA({
			registerType: 'autoUpdate',
			// never let the service worker answer API navigations with the app shell or a cached response
			workbox: { navigateFallbackDenylist: [/^\/api\//] },
			includeAssets: ['favicon.ico', 'logo.svg', 'apple-touch-icon-180x180.png'],
			manifest: {
				id: '/',
				name: 'Basecamp',
				short_name: 'Basecamp',
				description: 'My private hub',
				lang: 'en',
				start_url: '/',
				scope: '/',
				display: 'standalone',
				background_color: '#0f172a', // splash screen: the dark tile of the logo
				theme_color: '#f97316', // title bar / status bar: campfire orange
				categories: ['productivity', 'lifestyle'],
				// icons come from static/logo.svg via `pnpm generate-icons`
				icons: [
					{ src: 'pwa-64x64.png', sizes: '64x64', type: 'image/png' },
					{ src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
					{ src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
					{ src: 'maskable-icon-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' }
				],
				// long-press the installed icon for direct links
				shortcuts: [
					{
						name: 'CS2 Improvement',
						short_name: 'CS2',
						url: '/gaming/cs2/improvement',
						icons: [{ src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' }]
					},
					{
						name: 'Gaming',
						url: '/gaming',
						icons: [{ src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' }]
					}
				]
			}
		})
	],
	server: {
		proxy: {
			'/api': 'http://localhost:8080'
		}
	}
});
