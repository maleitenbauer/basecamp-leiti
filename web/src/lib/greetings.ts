/**
 * The warm welcome lines on the dashboard and the weekly report. Pure functions, no Svelte, so they're easy to check.
 *
 * They rotate: each pool steps through its lines one per day (or per week), so two days in a row never show the same
 * one, and every line gets its turn before any repeats. The same day always gives the same line, so nothing flickers
 * when the page re-renders. They notice how things are going — time of day, how much of today is done, how the week
 * went — and stay kind either way: encouraging when it went well, gentle when it didn't, never guilt.
 *
 * To add more: just add lines to a pool. Pools of different sizes are fine.
 */
export interface Greeting {
	headline: string;
	line: string;
}

export type DayPart = 'morning' | 'afternoon' | 'evening' | 'night';

export interface TodayProgressLike {
	done: number;
	total: number;
}

export function dayPart(hour: number): DayPart {
	if (hour >= 5 && hour < 12) return 'morning';
	if (hour >= 12 && hour < 18) return 'afternoon';
	if (hour >= 18 && hour < 22) return 'evening';
	return 'night';
}

function hash(text: string): number {
	let h = 2166136261;
	for (let i = 0; i < text.length; i++) {
		h ^= text.charCodeAt(i);
		h = Math.imul(h, 16777619);
	}
	return h >>> 0;
}

/**
 * The line for [step] (a day or week number): consecutive steps always differ and the whole pool is visited before
 * any repeat. [salt] gives each pool its own starting point so they don't all turn over in lockstep.
 */
export function rotate<T>(items: readonly T[], step: number, salt: string): T {
	return items[(step + hash(salt)) % items.length];
}

/** Whole days since 1970-01-01 for a YYYY-MM-DD date (time-zone independent). */
export function dayNumber(iso: string): number {
	const [y, m, d] = iso.split('-').map(Number);
	return Math.floor(Date.UTC(y, m - 1, d) / 86_400_000);
}

// ---------------------------------------------------------------- dashboard

const HEADLINES: Record<DayPart, string[]> = {
	morning: [
		'Good morning',
		'Morning, sunshine',
		'A fresh day is waiting',
		'Rise and shine',
		'Hello, early bird',
		'Good morning to you'
	],
	afternoon: [
		'Good afternoon',
		'Hope your day is going well',
		'Hello again',
		"Afternoon, and you're doing fine",
		'Welcome back',
		'Good to see you'
	],
	evening: [
		'Good evening',
		'Evening, you',
		'Welcome back',
		'Hope it was a good day',
		'Time to ease into the evening',
		'Good evening to you'
	],
	night: [
		'Still up?',
		'Quiet hours',
		'Hello, night owl',
		'Late one tonight',
		'Rest is productive too',
		'The day is winding down'
	]
};

const NEUTRAL = ["Glad you're here.", "Let's see how today looks.", "Take a breath. Here's your day.", 'Welcome back to base.'];

const NOTHING_PLANNED = [
	'Nothing planned for today. Enjoy the space.',
	'A clear day. Add something small if you feel like it.',
	'No pressure today, just good company.',
	'An open day is a gift.'
];

const NOT_STARTED = [
	(n: number) => `${n} on today's list. One small step is enough to start.`,
	(n: number) => `Start with whatever feels easiest. ${n} on the list today.`,
	() => "Today's list is ready whenever you are.",
	(n: number) => `Small steps count. ${n} on your list today.`
];

const NOT_STARTED_LATE = [
	() => "It's getting late. Do what feels right, and tomorrow is fresh anyway.",
	(n: number) => `${n} still open, and that's okay. Pick just one, or let it rest.`,
	() => 'No rush tonight. Whatever you manage is enough.'
];

const IN_PROGRESS = [
	(done: number, total: number) => `${done} of ${total} done already. Nicely moving.`,
	(done: number) => `You're ${done} in. Keep that rhythm going.`,
	() => 'Good progress. The rest will follow.',
	(done: number, total: number) => `${total - done} left, and you've already started. That's the hard part.`
];

const ALMOST_DONE = ["Just one left. You're so close.", "One more and you're done for today.", 'Nearly there. One to go.'];

const ALL_DONE = [
	'Everything is done. Enjoy the rest of your day.',
	"All clear. That's a good feeling.",
	'You did it all today. Well done.',
	'Nothing left on the list. Go enjoy it.'
];

/**
 * [progress] is how much of today's list is done, or null while that isn't known yet (still loading), in which case
 * the line stays neutral rather than claiming nothing is planned.
 */
export function dashboardGreeting(opts: { hour: number; date: string; progress: TodayProgressLike | null }): Greeting {
	const part = dayPart(opts.hour);
	const step = dayNumber(opts.date);
	const headline = rotate(HEADLINES[part], step, `headline-${part}`);
	const { progress } = opts;

	if (progress === null) return { headline, line: rotate(NEUTRAL, step, 'neutral') };
	const { done, total } = progress;
	if (total === 0) return { headline, line: rotate(NOTHING_PLANNED, step, 'nothing') };
	if (done >= total) return { headline, line: rotate(ALL_DONE, step, 'all-done') };
	if (total - done === 1 && done > 0) return { headline, line: rotate(ALMOST_DONE, step, 'almost') };
	if (done === 0) {
		const late = part === 'evening' || part === 'night';
		const pool = late ? NOT_STARTED_LATE : NOT_STARTED;
		return { headline, line: rotate(pool, step, late ? 'not-started-late' : 'not-started')(total) };
	}
	return { headline, line: rotate(IN_PROGRESS, step, 'in-progress')(done, total) };
}

// ------------------------------------------------------------ weekly report

type Tier = 'great' | 'solid' | 'mixed' | 'quiet' | 'none';

/** How the week went, from the report's overall 0..1 score (null when no module could score it). */
export function tierOf(score: number | null): Tier {
	if (score === null) return 'none';
	if (score >= 0.85) return 'great';
	if (score >= 0.6) return 'solid';
	if (score >= 0.3) return 'mixed';
	return 'quiet';
}

const WEEK_HEADLINES_DONE = ['Your week in review', 'Looking back at your week', 'Another week, done', 'Your week, at a glance'];
const WEEK_HEADLINES_NOW = ['Your week so far', 'How this week is going', 'The week so far'];

const WEEK_LINES_DONE: Record<Tier, string[]> = {
	great: [
		'What a week. You showed up, and it shows.',
		'Look at that consistency. You should be proud.',
		'A really strong week. Take a moment to enjoy it.',
		'You kept your promises to yourself this week.'
	],
	solid: [
		'A solid week, with plenty to be proud of.',
		'Steady and good. That is how habits are built.',
		'You did more than you probably give yourself credit for.',
		'A good week, and a good base to build on.'
	],
	mixed: [
		'A mixed week, and that is okay. Small steps still count.',
		'Some days flowed, some did not. Both are part of it.',
		'Progress is not a straight line. You are still moving.',
		'Be kind to yourself, and look at what did go well.'
	],
	quiet: [
		'A quiet week. A fresh Monday is always waiting for you.',
		'Rest weeks happen. Pick one small thing to restart with.',
		'Not every week is a big one, and that is fine.',
		'Whatever this week was, you are here, and that counts.'
	],
	none: ['Here is your week at a glance.', 'A look back at your week.', 'Your week, in numbers.']
};

const WEEK_LINES_NOW: Record<Tier, string[]> = {
	great: ['A strong start. Keep it going.', 'Great pace so far. Enjoy it.', 'You are on a roll this week.'],
	solid: ['Good pace so far. The week is yours.', 'Steady going. Keep that rhythm.', 'A good week is taking shape.'],
	mixed: [
		'There is still time to shape the rest of the week.',
		'A slower start is fine. Plenty of week left.',
		'Small steps from here still add up.'
	],
	quiet: [
		'Plenty of week left. One small thing is a fine start.',
		'No need to catch up. Just begin where you are.',
		'The week is still wide open.'
	],
	none: ["Here's how the week is shaping up.", 'A look at the week so far.', 'Your week, in progress.']
};

/** Seeded by the week, so it stays put while you look at a week and changes from one week to the next. */
export function weeklyGreeting(opts: { weekStart: string; score: number | null; inProgress: boolean }): Greeting {
	const step = Math.floor(dayNumber(opts.weekStart) / 7);
	const tier = tierOf(opts.score);
	const kind = opts.inProgress ? 'now' : 'done';
	return {
		headline: rotate(opts.inProgress ? WEEK_HEADLINES_NOW : WEEK_HEADLINES_DONE, step, `week-headline-${kind}`),
		line: rotate((opts.inProgress ? WEEK_LINES_NOW : WEEK_LINES_DONE)[tier], step, `week-line-${kind}-${tier}`)
	};
}
