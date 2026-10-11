<script lang="ts">
	import SubmoduleShell from '$lib/components/SubmoduleShell.svelte';

	let { children } = $props();
</script>

<SubmoduleShell moduleId="report" submoduleId="weekly">
	{@render children()}
</SubmoduleShell>
