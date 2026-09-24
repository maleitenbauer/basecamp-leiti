<script lang="ts">
	import SubmoduleShell from '$lib/components/SubmoduleShell.svelte';

	let { children } = $props();
</script>

<SubmoduleShell moduleId="lifestyle" submoduleId="todos">
	{@render children()}
</SubmoduleShell>
