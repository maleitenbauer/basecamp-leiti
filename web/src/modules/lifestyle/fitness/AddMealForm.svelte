<script lang="ts">
	import { createMutation, createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { nutritionApi } from './api';
	import { kcalFor, localTimeString, toInstant, type Food, type OnlineFood } from './types';

	let { date, foods }: { date: string; foods: Food[] } = $props();

	const queryClient = useQueryClient();
	const refresh = () => queryClient.invalidateQueries({ queryKey: ['lifestyle', 'fitness', 'nutrition'] });

	type Mode = 'food' | 'custom' | 'quick';
	type Picked = { kind: 'food'; food: Food } | { kind: 'online'; item: OnlineFood };

	let mode = $state<Mode>('food');
	let query = $state('');
	let picked = $state<Picked | null>(null);
	let grams = $state('');
	let time = $state(localTimeString());

	// ---- finding a food: your own library first, Open Food Facts when that has nothing ----

	const text = $derived(query.trim());

	function usageOrder(a: Food, b: Food): number {
		return (
			(b.lastUsedAt ?? '').localeCompare(a.lastUsedAt ?? '') ||
			b.useCount - a.useCount ||
			a.name.localeCompare(b.name)
		);
	}

	// With nothing typed, show what you ate most recently; otherwise filter the library by name or brand.
	const localMatches = $derived.by(() => {
		const q = text.toLowerCase();
		const pool = q
			? foods.filter((f) => f.name.toLowerCase().includes(q) || (f.brand ?? '').toLowerCase().includes(q))
			: foods;
		return [...pool].sort(usageOrder).slice(0, 8);
	});

	const isBarcode = (s: string) => /^\d{8,14}$/.test(s);

	let debounced = $state('');
	$effect(() => {
		const current = text;
		const timer = setTimeout(() => (debounced = current), 500);
		return () => clearTimeout(timer);
	});

	let forced = $state<string | null>(null);

	// The online lookup runs on its own for barcodes, or when typing has paused and your library has no match —
	// otherwise only when you ask for it, so it isn't called on every keystroke.
	const onlineText = $derived.by(() => {
		if (text.length < 2) return null;
		if (forced === text) return text;
		if (debounced === text && (isBarcode(text) || (text.length >= 3 && localMatches.length === 0))) return text;
		return null;
	});

	const online = createQuery(() => ({
		queryKey: ['lifestyle', 'fitness', 'nutrition', 'online', onlineText],
		queryFn: () => nutritionApi.searchOnline(onlineText!),
		enabled: onlineText !== null,
		staleTime: 5 * 60_000,
		retry: false
	}));

	function pickFood(food: Food) {
		picked = { kind: 'food', food };
		grams = String(food.lastGrams ?? food.portionGrams ?? 100);
	}

	function pickOnline(item: OnlineFood) {
		picked = { kind: 'online', item };
		grams = '100';
	}

	// ---- amount and calories ----

	const pickedName = $derived(picked ? (picked.kind === 'food' ? picked.food.name : picked.item.name) : '');
	const pickedBrand = $derived(picked ? (picked.kind === 'food' ? picked.food.brand : picked.item.brand) : null);
	const per100 = $derived(picked ? (picked.kind === 'food' ? picked.food.kcalPer100g : picked.item.kcalPer100g) : 0);
	const gramsNum = $derived(Number(grams));
	const previewKcal = $derived(Number.isFinite(gramsNum) && gramsNum > 0 ? kcalFor(per100, gramsNum) : null);
	const portion = $derived(picked?.kind === 'food' && picked.food.portionGrams ? picked.food : null);

	function reset() {
		picked = null;
		query = '';
		debounced = '';
		forced = null;
		grams = '';
		time = localTimeString();
	}

	const addMeal = createMutation(() => ({
		mutationFn: async () => {
			const p = picked!;
			let foodId: number;
			if (p.kind === 'food') {
				foodId = p.food.id;
			} else {
				// copy it into your own library once, so it works offline from now on
				const saved = await nutritionApi.createFood({
					name: p.item.name,
					brand: p.item.brand ?? undefined,
					kcalPer100g: p.item.kcalPer100g,
					barcode: p.item.code ?? undefined,
					source: 'OPEN_FOOD_FACTS'
				});
				foodId = saved.id;
			}
			return nutritionApi.addEntry({ day: date, eatenAt: toInstant(date, time), foodId, grams: gramsNum });
		},
		onSuccess: () => {
			reset();
			refresh();
		}
	}));

	const deleteFood = createMutation(() => ({
		mutationFn: (id: number) => nutritionApi.deleteFood(id),
		onSuccess: refresh
	}));

	// ---- creating your own food ----

	let customName = $state('');
	let customBrand = $state('');
	let customKcal = $state('');
	let customPortionGrams = $state('');
	let customPortionLabel = $state('');

	const createFood = createMutation(() => ({
		mutationFn: () =>
			nutritionApi.createFood({
				name: customName,
				brand: customBrand || undefined,
				kcalPer100g: Number(customKcal),
				portionGrams: customPortionGrams ? Number(customPortionGrams) : undefined,
				portionLabel: customPortionLabel || undefined
			}),
		onSuccess: (food) => {
			customName = customBrand = customKcal = customPortionGrams = customPortionLabel = '';
			mode = 'food';
			pickFood(food);
			refresh();
		}
	}));

	// ---- quick entry: just calories ----

	let quickName = $state('');
	let quickKcal = $state('');

	const quickAdd = createMutation(() => ({
		mutationFn: () =>
			nutritionApi.addEntry({
				day: date,
				eatenAt: toInstant(date, time),
				name: quickName || undefined,
				kcal: Math.round(Number(quickKcal))
			}),
		onSuccess: () => {
			quickName = quickKcal = '';
			time = localTimeString();
			refresh();
		}
	}));

	const tab = (m: Mode) =>
		`rounded px-2 py-1 text-xs ${mode === m ? 'bg-brand-500/20 text-brand-300' : 'text-slate-400 hover:bg-slate-800'}`;
	const input = 'rounded-md bg-slate-800 px-3 py-2 text-sm';
	const primary =
		'rounded-md bg-brand-500 px-4 py-2 text-sm font-medium text-slate-950 hover:bg-brand-400 disabled:opacity-50';
</script>

<section class="space-y-3 rounded-lg border border-slate-700 p-4">
	<div class="flex items-center gap-1">
		<h3 class="mr-2 text-sm font-semibold">Add a meal</h3>
		<button class={tab('food')} onclick={() => (mode = 'food')}>Find food</button>
		<button class={tab('custom')} onclick={() => (mode = 'custom')}>New food</button>
		<button class={tab('quick')} onclick={() => (mode = 'quick')}>Just calories</button>
	</div>

	{#if mode === 'food'}
		{#if !picked}
			<div class="space-y-2">
				<input
					bind:value={query}
					placeholder="Search your foods, or type a name or barcode…"
					maxlength="100"
					autocomplete="off"
					class="w-full {input}"
				/>

				{#if localMatches.length > 0}
					<p class="text-xs text-slate-500">{text ? 'In your foods' : 'Recent'}</p>
					<ul class="divide-y divide-slate-800 rounded-md border border-slate-800">
						{#each localMatches as f (f.id)}
							<li class="flex items-center">
								<button
									onclick={() => pickFood(f)}
									class="flex min-w-0 flex-1 items-center justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-slate-800"
								>
									<span class="truncate">
										{f.name}{#if f.brand}<span class="text-slate-500"> · {f.brand}</span>{/if}
									</span>
									<span class="shrink-0 text-xs text-slate-500 tabular-nums">{f.kcalPer100g} kcal/100 g</span>
								</button>
								<button
									onclick={() => confirm(`Remove "${f.name}" from your foods? Past meals keep their calories.`) && deleteFood.mutate(f.id)}
									class="px-2 text-xs text-slate-600 hover:text-red-400"
									title="Remove from your foods"
								>
									✕
								</button>
							</li>
						{/each}
					</ul>
				{:else if text}
					<p class="text-sm text-slate-500">Nothing in your foods matches “{text}”.</p>
				{/if}

				{#if text.length >= 2 && onlineText === null}
					<button onclick={() => (forced = text)} class="text-sm text-brand-400 hover:text-brand-300">
						Search Open Food Facts for “{text}”
					</button>
				{/if}

				{#if onlineText !== null}
					<p class="text-xs text-slate-500">Open Food Facts</p>
					{#if online.isPending}
						<p class="text-sm text-slate-400">Searching…</p>
					{:else if online.isError}
						<p class="text-sm text-red-400">{online.error.message}</p>
					{:else if online.data.length === 0}
						<p class="text-sm text-slate-500">No products with calorie info found.</p>
					{:else}
						<ul class="divide-y divide-slate-800 rounded-md border border-slate-800">
							{#each online.data as item, i (item.code ?? i)}
								<li>
									<button
										onclick={() => pickOnline(item)}
										class="flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-slate-800"
									>
										<span class="truncate">
											{item.name}{#if item.brand}<span class="text-slate-500"> · {item.brand}</span>{/if}
										</span>
										<span class="shrink-0 text-xs text-slate-500 tabular-nums">{item.kcalPer100g} kcal/100 g</span>
									</button>
								</li>
							{/each}
						</ul>
						<p class="text-xs text-slate-600">
							Data from <a href="https://openfoodfacts.org" target="_blank" rel="noopener noreferrer" class="underline">Open Food Facts</a>
							(ODbL). Crowd-sourced, so check the numbers.
						</p>
					{/if}
				{/if}
			</div>
		{:else}
			<form
				class="space-y-3"
				onsubmit={(e) => {
					e.preventDefault();
					if (previewKcal !== null) addMeal.mutate();
				}}
			>
				<div class="flex items-start justify-between gap-3">
					<div class="min-w-0">
						<p class="truncate font-medium">{pickedName}</p>
						<p class="text-xs text-slate-500">
							{#if pickedBrand}{pickedBrand} · {/if}{per100} kcal per 100 g
						</p>
					</div>
					<button type="button" onclick={() => (picked = null)} class="text-xs text-slate-500 hover:text-slate-200">
						Change
					</button>
				</div>

				<div class="flex flex-wrap items-center gap-2">
					<input bind:value={grams} type="number" min="0.1" step="any" class="w-24 {input}" aria-label="Amount in grams" />
					<span class="text-sm text-slate-400">g</span>
					{#if portion}
						<button
							type="button"
							onclick={() => (grams = String(portion.portionGrams))}
							class="rounded-md border border-slate-700 px-2 py-1 text-xs text-slate-300 hover:bg-slate-800"
						>
							1 {portion.portionLabel || 'portion'} ({portion.portionGrams} g)
						</button>
					{/if}
					<input bind:value={time} type="time" class="{input} ml-auto" aria-label="Time eaten" />
				</div>

				<div class="flex items-center justify-between gap-3">
					<p class="text-lg font-semibold tabular-nums">
						{previewKcal ?? '–'} <span class="text-sm font-normal text-slate-400">kcal</span>
					</p>
					<button type="submit" disabled={addMeal.isPending || previewKcal === null} class={primary}>Add to day</button>
				</div>
				{#if addMeal.isError}<p class="text-sm text-red-400">{addMeal.error.message}</p>{/if}
			</form>
		{/if}
	{:else if mode === 'custom'}
		<form
			class="space-y-2"
			onsubmit={(e) => {
				e.preventDefault();
				createFood.mutate();
			}}
		>
			<div class="flex gap-2">
				<input bind:value={customName} placeholder="Name" maxlength="200" required class="min-w-0 flex-1 {input}" />
				<input bind:value={customBrand} placeholder="Brand (optional)" maxlength="200" class="min-w-0 flex-1 {input}" />
			</div>
			<div class="flex flex-wrap items-center gap-2">
				<input bind:value={customKcal} type="number" min="0" max="1000" step="any" required placeholder="kcal per 100 g" class="w-36 {input}" />
				<input bind:value={customPortionGrams} type="number" min="0.1" step="any" placeholder="Portion (g)" class="w-32 {input}" />
				<input bind:value={customPortionLabel} placeholder="Portion name, e.g. slice" maxlength="60" class="min-w-0 flex-1 {input}" />
			</div>
			<div class="flex items-center justify-between gap-3">
				<p class="text-xs text-slate-500">Saved to your foods, so next time it's one tap.</p>
				<button type="submit" disabled={createFood.isPending || !customName.trim() || customKcal === ''} class={primary}>
					Save food
				</button>
			</div>
			{#if createFood.isError}<p class="text-sm text-red-400">{createFood.error.message}</p>{/if}
		</form>
	{:else}
		<form
			class="space-y-2"
			onsubmit={(e) => {
				e.preventDefault();
				quickAdd.mutate();
			}}
		>
			<div class="flex flex-wrap items-center gap-2">
				<input bind:value={quickName} placeholder="What was it? (optional)" maxlength="200" class="min-w-0 flex-1 {input}" />
				<input bind:value={quickKcal} type="number" min="0" max="20000" step="1" required placeholder="kcal" class="w-24 {input}" />
				<input bind:value={time} type="time" class={input} aria-label="Time eaten" />
				<button type="submit" disabled={quickAdd.isPending || quickKcal === ''} class={primary}>Add</button>
			</div>
			{#if quickAdd.isError}<p class="text-sm text-red-400">{quickAdd.error.message}</p>{/if}
		</form>
	{/if}
</section>
