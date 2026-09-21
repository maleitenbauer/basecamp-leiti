<script lang="ts">
	import SubmoduleShell from '$lib/components/SubmoduleShell.svelte';

	let { children } = $props();
</script>

<SubmoduleShell moduleId="logbook" submoduleId="todos">
	{@render children()}
</SubmoduleShell>
