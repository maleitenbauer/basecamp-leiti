<script lang="ts">
	import { page } from '$app/state';
	import { findSubmodule } from '$modules/registry';
	import type { Snippet } from 'svelte';

	let {
		moduleId,
		submoduleId,
		children
	}: { moduleId: string; submoduleId: string; children: Snippet } = $props();

	const found = $derived(findSubmodule(moduleId, submoduleId));
</script>

{#if found}
	<div class="mx-auto max-w-4xl px-4 pt-4 sm:px-6">
		<div class="text-sm text-slate-400">
			<a href={found.module.href} class="hover:text-white">{found.module.icon} {found.module.name}</a>
			<span class="text-slate-600">/</span>
			<span class="text-slate-200">{found.submodule.name}</span>
		</div>
		<nav class="mt-3 flex gap-1 overflow-x-auto border-b border-slate-800" aria-label="Pages">
			{#each found.submodule.pages as p (p.id)}
				<a
					href={p.href}
					aria-current={page.url.pathname === p.href ? 'page' : undefined}
					class="-mb-px border-b-2 px-3 py-2 text-sm whitespace-nowrap {page.url.pathname === p.href
						? 'border-brand-500 text-white'
						: 'border-transparent text-slate-400 hover:text-white'}"
				>
					{p.name}
				</a>
			{/each}
		</nav>
	</div>
{/if}
{@render children()}
