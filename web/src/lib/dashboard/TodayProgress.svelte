<script lang="ts">
	import type { ProgressPart } from './types';

	let { parts }: { parts: ProgressPart[] } = $props();

	const done = $derived(parts.reduce((sum, p) => sum + p.done, 0));
	const total = $derived(parts.reduce((sum, p) => sum + p.total, 0));
	const percent = $derived(total === 0 ? 0 : Math.round((done / total) * 100));
	const complete = $derived(total > 0 && done >= total);
</script>

{#if total > 0}
	<section class="rounded-lg border border-slate-700 p-4" aria-label="Today's progress">
		<div class="flex items-baseline justify-between gap-3">
			<h2 class="text-sm font-medium text-slate-300">Today</h2>
			<p class="text-sm text-slate-400">
				<span class="text-lg font-semibold tabular-nums {complete ? 'text-emerald-400' : 'text-slate-100'}">{done}</span>
				of {total} done
			</p>
		</div>

		<div
			class="mt-2 h-2.5 overflow-hidden rounded-full bg-slate-800"
			role="progressbar"
			aria-valuenow={percent}
			aria-valuemin="0"
			aria-valuemax="100"
			aria-label="{percent}% of today's things done"
		>
			<div
				class="h-full rounded-full transition-[width] duration-500 {complete ? 'bg-emerald-400' : 'bg-brand-400'}"
				style="width: {percent}%"
			></div>
		</div>

		<ul class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-500">
			{#each parts as part (part.id)}
				<li>
					{part.title}
					<span class="tabular-nums {part.done >= part.total ? 'text-emerald-400' : 'text-slate-300'}">{part.done}/{part.total}</span>
				</li>
			{/each}
		</ul>
		{#if complete}<p class="mt-2 text-xs text-emerald-400">Everything planned for today is done.</p>{/if}
	</section>
{/if}
