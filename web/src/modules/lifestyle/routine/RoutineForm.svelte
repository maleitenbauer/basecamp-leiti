<script lang="ts">
	import { createMutation, useQueryClient } from '@tanstack/svelte-query';
	import { untrack } from 'svelte';
	import { today } from '$lib/dates';
	import { routineApi } from './api';
	import {
		MONTHS,
		ORDINALS,
		WEEKDAYS,
		dateFacts,
		type EndType,
		type RecurUnit,
		type RecurrenceInput,
		type Routine,
		type ScheduleType
	} from './types';

	/** Creates a routine, or edits [routine] when given. [onDone] runs after a successful save. */
	let { routine, onDone }: { routine?: Routine; onDone?: () => void } = $props();

	const queryClient = useQueryClient();

	type MonthChoice = 'DAY' | 'LAST_DAY' | 'NTH' | 'LAST_WEEKDAY';

	function initialMonthChoice(): MonthChoice {
		const rule = routine?.recurrence;
		if (!rule || rule.unit !== 'MONTH') return 'DAY';
		if (rule.monthMode === 'LAST_DAY') return 'LAST_DAY';
		if (rule.monthMode === 'NTH_WEEKDAY') return rule.nth === 5 ? 'LAST_WEEKDAY' : 'NTH';
		return 'DAY';
	}

	// the form starts from the routine's current values on purpose; it isn't kept in sync afterwards
	const rule = untrack(() => routine?.recurrence ?? null);
	let name = $state(untrack(() => routine?.name ?? ''));
	let note = $state(untrack(() => routine?.note ?? ''));
	let scheduleType = $state<ScheduleType>(untrack(() => routine?.scheduleType ?? 'RECURRING'));
	let timesPerWeek = $state(untrack(() => routine?.timesPerWeek ?? 3));
	let timed = $state(untrack(() => routine?.targetMinutes != null));
	let minutes = $state(untrack(() => String(routine?.targetMinutes ?? 10)));
	let carryOver = $state(untrack(() => routine?.carryOver ?? false));

	const initialStart = rule?.startDate ?? today();
	let startDate = $state(initialStart);
	let unit = $state<RecurUnit>(rule?.unit ?? 'DAY');
	let interval = $state(String(rule?.interval ?? 1));
	let weekdays = $state<number[]>(rule?.weekdays?.length ? [...rule.weekdays] : [dateFacts(initialStart).weekday]);
	let monthChoice = $state<MonthChoice>(initialMonthChoice());
	let monthDay = $state(String(rule?.monthDay ?? dateFacts(initialStart).day));
	let endType = $state<EndType>(rule?.endType ?? 'NEVER');
	let endDate = $state(rule?.endDate ?? '');
	let endCount = $state(String(rule?.endCount ?? 10));

	const facts = $derived(dateFacts(startDate || today()));

	// "the second Tuesday" is only offered when the start date is one; a 5th weekday can only mean "the last"
	const offersNth = $derived(facts.nth <= 4);
	const offersLastWeekday = $derived(facts.isLastOfWeekday);

	// switching the start date can make the chosen monthly option impossible; fall back to a day of the month
	$effect(() => {
		if ((monthChoice === 'NTH' && !offersNth) || (monthChoice === 'LAST_WEEKDAY' && !offersLastWeekday)) {
			monthChoice = 'DAY';
		}
	});

	function toggleWeekday(day: number) {
		if (weekdays.includes(day)) {
			if (weekdays.length > 1) weekdays = weekdays.filter((d) => d !== day);
		} else {
			weekdays = [...weekdays, day].sort((a, b) => a - b);
		}
	}

	function buildRecurrence(): RecurrenceInput {
		const result: RecurrenceInput = {
			unit,
			interval: Math.max(1, Math.round(Number(interval))),
			weekdays: unit === 'WEEK' ? weekdays : [],
			monthMode: 'DAY_OF_MONTH',
			startDate,
			endType
		};
		if (unit === 'MONTH') {
			if (monthChoice === 'DAY') result.monthDay = Math.round(Number(monthDay));
			else if (monthChoice === 'LAST_DAY') result.monthMode = 'LAST_DAY';
			else {
				result.monthMode = 'NTH_WEEKDAY';
				result.nth = monthChoice === 'NTH' ? facts.nth : 5;
				result.weekday = facts.weekday;
			}
		}
		if (unit === 'YEAR') {
			result.yearMonth = facts.month;
			result.monthDay = facts.day;
		}
		if (endType === 'ON_DATE') result.endDate = endDate;
		if (endType === 'AFTER_COUNT') result.endCount = Math.round(Number(endCount));
		return result;
	}

	const save = createMutation(() => ({
		mutationFn: async () => {
			const body = {
				name: name.trim(),
				note: note.trim() || null,
				scheduleType,
				timesPerWeek: scheduleType === 'TIMES_PER_WEEK' ? timesPerWeek : 1,
				recurrence: scheduleType === 'RECURRING' ? buildRecurrence() : null,
				carryOver: scheduleType === 'RECURRING' && carryOver,
				targetMinutes: timed ? Math.round(Number(minutes)) : null
			};
			if (routine) await routineApi.update(routine.id, body);
			else await routineApi.create(body);
		},
		onSuccess: () => {
			if (!routine) {
				name = note = '';
				scheduleType = 'RECURRING';
				unit = 'DAY';
				interval = '1';
				endType = 'NEVER';
				carryOver = false;
				timed = false;
			}
			queryClient.invalidateQueries({ queryKey: ['lifestyle', 'routine'] });
			onDone?.();
		}
	}));

	const valid = $derived(
		name.trim().length > 0 &&
			(!timed || (Number(minutes) >= 1 && Number(minutes) <= 1440)) &&
			(scheduleType === 'TIMES_PER_WEEK' ||
				(startDate !== '' &&
					Number(interval) >= 1 &&
					(endType !== 'ON_DATE' || (endDate !== '' && endDate >= startDate)) &&
					(endType !== 'AFTER_COUNT' || (Number(endCount) >= 1 && Number(endCount) <= 1000)) &&
					(unit !== 'MONTH' || monthChoice !== 'DAY' || (Number(monthDay) >= 1 && Number(monthDay) <= 31))))
	);

	const input = 'rounded-md bg-slate-800 px-3 py-2 text-sm';
	const label = 'flex items-center gap-2 text-sm text-slate-300';
	const choice = (active: boolean) =>
		`rounded px-3 py-1.5 text-sm ${active ? 'bg-brand-500/20 text-brand-300' : 'text-slate-400 hover:bg-slate-800'}`;
	const plural = (n: string) => (Number(n) === 1 ? '' : 's');
</script>

<form
	onsubmit={(e) => {
		e.preventDefault();
		if (valid) save.mutate();
	}}
	class="space-y-4 rounded-lg border border-slate-700 p-3"
>
	<input bind:value={name} placeholder="Routine, e.g. Meditation" maxlength="200" required class="w-full {input}" />

	<div class="space-y-3">
		<div class="flex gap-1" role="group" aria-label="Kind of schedule">
			<button type="button" class={choice(scheduleType === 'RECURRING')} onclick={() => (scheduleType = 'RECURRING')}>
				On a schedule
			</button>
			<button
				type="button"
				class={choice(scheduleType === 'TIMES_PER_WEEK')}
				onclick={() => (scheduleType = 'TIMES_PER_WEEK')}
			>
				Times a week
			</button>
		</div>

		{#if scheduleType === 'TIMES_PER_WEEK'}
			<label class={label}>
				<input bind:value={timesPerWeek} type="number" min="1" max="7" class="w-16 {input}" aria-label="Times per week" />
				time{timesPerWeek === 1 ? '' : 's'} a week, on whichever days suit
			</label>
		{:else}
			<div class="space-y-3">
				<label class={label}>
					Starts
					<input bind:value={startDate} type="date" required class={input} />
				</label>

				<div class="flex flex-wrap items-center gap-2">
					<span class="text-sm text-slate-300">Repeat every</span>
					<input bind:value={interval} type="number" min="1" max="999" class="w-20 {input}" aria-label="Repeat every" />
					<select bind:value={unit} class={input} aria-label="Unit">
						<option value="DAY">day{plural(interval)}</option>
						<option value="WEEK">week{plural(interval)}</option>
						<option value="MONTH">month{plural(interval)}</option>
						<option value="YEAR">year{plural(interval)}</option>
					</select>
				</div>

				{#if unit === 'WEEK'}
					<div class="flex flex-wrap gap-1" role="group" aria-label="On these days">
						{#each WEEKDAYS as dayName, i (dayName)}
							<button
								type="button"
								onclick={() => toggleWeekday(i + 1)}
								aria-pressed={weekdays.includes(i + 1)}
								title={dayName}
								class="h-8 w-10 rounded text-xs {weekdays.includes(i + 1)
									? 'bg-brand-500 text-slate-950'
									: 'bg-slate-800 text-slate-400 hover:bg-slate-700'}"
							>
								{dayName.slice(0, 3)}
							</button>
						{/each}
					</div>
				{:else if unit === 'MONTH'}
					<div class="flex flex-wrap items-center gap-2">
						<select bind:value={monthChoice} class={input} aria-label="Which day of the month">
							<option value="DAY">On day …</option>
							<option value="LAST_DAY">On the last day</option>
							{#if offersNth}
								<option value="NTH">On the {ORDINALS[facts.nth - 1]} {WEEKDAYS[facts.weekday - 1]}</option>
							{/if}
							{#if offersLastWeekday}
								<option value="LAST_WEEKDAY">On the last {WEEKDAYS[facts.weekday - 1]}</option>
							{/if}
						</select>
						{#if monthChoice === 'DAY'}
							<input bind:value={monthDay} type="number" min="1" max="31" class="w-20 {input}" aria-label="Day of the month" />
							{#if Number(monthDay) > 28}
								<span class="text-xs text-slate-500">in shorter months, the last day</span>
							{/if}
						{/if}
					</div>
				{:else if unit === 'YEAR'}
					<p class="text-sm text-slate-400">On {MONTHS[facts.month - 1]} {facts.day}</p>
				{/if}

				<div class="space-y-1.5">
					<p class="text-sm text-slate-400">Ends</p>
					<label class={label}>
						<input type="radio" bind:group={endType} value="NEVER" /> Never
					</label>
					<label class={label}>
						<input type="radio" bind:group={endType} value="ON_DATE" /> On
						<input bind:value={endDate} type="date" min={startDate} disabled={endType !== 'ON_DATE'} class={input} aria-label="End date" />
					</label>
					<label class={label}>
						<input type="radio" bind:group={endType} value="AFTER_COUNT" /> After
						<input
							bind:value={endCount}
							type="number"
							min="1"
							max="1000"
							disabled={endType !== 'AFTER_COUNT'}
							class="w-20 {input}"
							aria-label="Number of times"
						/>
						time{plural(endCount)}
					</label>
				</div>

				<label class={label}>
					<input type="checkbox" bind:checked={carryOver} class="h-4 w-4" />
					Keep reminding me until it's done
				</label>
			</div>
		{/if}
	</div>

	<div class="flex flex-wrap items-center gap-3">
		<label class={label}>
			<input type="checkbox" bind:checked={timed} class="h-4 w-4" />
			Takes a set time
		</label>
		{#if timed}
			<label class={label}>
				<input bind:value={minutes} type="number" min="1" max="1440" class="w-20 {input}" aria-label="Minutes" />
				min
			</label>
		{/if}
	</div>

	<input bind:value={note} placeholder="Note (optional)" maxlength="500" class="w-full {input}" />

	<div class="flex items-center gap-2">
		<button
			type="submit"
			disabled={save.isPending || !valid}
			class="rounded-md bg-brand-500 px-4 py-2 text-sm font-medium text-slate-950 hover:bg-brand-400 disabled:opacity-50"
		>
			{routine ? 'Save' : 'Add routine'}
		</button>
		{#if routine}
			<button type="button" onclick={() => onDone?.()} class="text-sm text-slate-400 hover:text-slate-200">Cancel</button>
		{/if}
		{#if save.isError}<span class="text-sm text-red-400">{save.error.message}</span>{/if}
	</div>
</form>
