<script module lang="ts">
	// remember which optional radar images do not exist, so re-rendering a demo doesn't re-request them
	const missingRadar = new Set<string>();
</script>

<script lang="ts">
	import { mapSlug } from '../matches/maps';
	import { hasRadarCalibration } from './radarCalibration';

	let { map }: { map: string | null } = $props();

	const slug = $derived(mapSlug(map));
	const calibrated = $derived(hasRadarCalibration(map));

	let failed = $state(false);
	const showImage = $derived(calibrated && !failed && !missingRadar.has(slug));
</script>

{#if showImage}
	<img
		src="/maps/radar/{slug}.png"
		alt=""
		class="absolute inset-0 h-full w-full object-cover"
		onerror={() => {
			missingRadar.add(slug);
			failed = true;
		}}
	/>
{:else if !calibrated}
	<svg viewBox="0 0 100 100" class="absolute inset-0 h-full w-full text-slate-700" aria-hidden="true">
		{#each [20, 40, 60, 80] as g (g)}
			<line x1={g} y1="0" x2={g} y2="100" stroke="currentColor" stroke-width="0.2" />
			<line x1="0" y1={g} x2="100" y2={g} stroke="currentColor" stroke-width="0.2" />
		{/each}
	</svg>
{/if}
