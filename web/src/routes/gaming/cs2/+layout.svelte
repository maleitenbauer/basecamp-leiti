<script lang="ts">
	import SubmoduleShell from '$lib/components/SubmoduleShell.svelte';

	let { children } = $props();
</script>

<SubmoduleShell moduleId="gaming" submoduleId="cs2">
	{@render children()}
</SubmoduleShell>
