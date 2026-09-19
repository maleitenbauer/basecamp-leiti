<script lang="ts">
	import { findModule } from '$modules/registry';

	let { moduleId }: { moduleId: string } = $props();
	const module = $derived(findModule(moduleId));
</script>

{#if module}
	<main class="mx-auto max-w-4xl p-4 sm:p-6">
		<h1 class="text-2xl font-semibold">{module.icon} {module.name}</h1>
		<p class="mt-1 text-slate-400">{module.description}</p>

		<div class="mt-6 grid gap-3 sm:grid-cols-2">
			{#each module.submodules as sub (sub.id)}
				<a href={sub.href} class="rounded-lg border border-slate-700 p-4 hover:border-slate-500">
					<div class="font-medium">{sub.name}</div>
					<div class="mt-1 text-sm text-slate-400">{sub.description}</div>
					<div class="mt-2 text-xs text-slate-500">{sub.pages.map((p) => p.name).join(' · ')}</div>
				</a>
			{/each}
		</div>
	</main>
{:else}
	<main class="mx-auto max-w-4xl p-6 text-slate-400">Unknown module.</main>
{/if}
