<script module lang="ts">
	// remember which optional map images do not exist, so a list of 40 matches does not request them 40 times
	const missing = new Set<string>();
</script>

<script lang="ts">
	import { mapInitial, mapSlug, mapStyle } from './maps';

	let { map }: { map: string | null } = $props();

	const slug = $derived(mapSlug(map));
	const style = $derived(mapStyle(map));
	let failed = $state(false);
	const showImage = $derived(slug !== '' && !failed && !missing.has(slug));
</script>

<div
	class="relative h-full w-full overflow-hidden"
	style="background: linear-gradient(135deg, {style.from}, {style.to})"
	aria-hidden="true"
>
	<div
		class="absolute inset-0 opacity-30"
		style="background-image: repeating-linear-gradient(45deg, rgb(255 255 255 / 0.16) 0 2px, transparent 2px 14px)"
	></div>
	<span
		class="absolute -bottom-[0.18em] -left-[0.04em] text-[5rem] leading-none font-black text-white/15 select-none"
	>
		{mapInitial(map)}
	</span>
	{#if showImage}
		<img
			src="/maps/{slug}.png"
			alt=""
			loading="lazy"
			class="absolute inset-0 h-full w-full object-cover"
			onerror={() => {
				missing.add(slug);
				failed = true;
			}}
		/>
	{/if}
	<div class="absolute inset-0 bg-gradient-to-r from-black/30 via-black/10 to-transparent"></div>
</div>
