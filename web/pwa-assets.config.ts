import { defineConfig, minimal2023Preset } from '@vite-pwa/assets-generator/config';

// Generates every icon size from static/logo.svg:  pnpm generate-icons
// The maskable and Apple icons are drawn on the same dark tile colour, so the logo tile blends in seamlessly.
const background = '#0f172a';

export default defineConfig({
	headLinkOptions: { preset: '2023' },
	preset: {
		...minimal2023Preset,
		maskable: { ...minimal2023Preset.maskable, resizeOptions: { background } },
		apple: { ...minimal2023Preset.apple, resizeOptions: { background } }
	},
	images: ['static/logo.svg']
});
