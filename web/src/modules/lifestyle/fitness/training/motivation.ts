/**
 * The motivation lines around a workout: when you press Start, while you train, and when you finish. Pure functions
 * built on the same rotation as the dashboard greetings (see $lib/greetings): the same day always gives the same
 * line, the next day a different one, and no line is guilt-tripping. To add more, add to a pool.
 */
import { dayNumber, dayPart, rotate, type DayPart, type Greeting } from '$lib/greetings';
import { minutesText } from './format';

// ---------------------------------------------------------------- start

export interface StartContext {
	date: string;
	hour: number;
	/** The plan being started, null for an empty workout. */
	planName: string | null;
	workoutsThisWeek: number;
	weeklyTarget: number | null;
	daysSinceLast: number | null;
	totalWorkouts: number;
}

const START_HEADLINES: Record<DayPart, string[]> = {
	morning: ['Morning session. Great start', 'Early bird gets the gains', 'A strong way to begin the day', 'Rise and lift'],
	afternoon: ['Afternoon session. Nice', "Midday power-up", "Let's make the afternoon count", 'Time to move'],
	evening: ['Evening session. Well done for showing up', 'End the day strong', 'Evening iron time', 'Make the evening count'],
	night: ['Late session. Respect', 'Night owl training', "Late but here. That's what counts", 'The quiet gym hours']
};

const FIRST_EVER = [
	'Your very first workout here. Everyone starts somewhere, and you showed up.',
	'First one logged is the hardest one. After this, it is just a habit.'
];

const COMEBACK = [
	(days: number) => `${days} days since the last one. Welcome back, that is all that matters.`,
	() => 'Back at it. No catching up needed, just start with a good one.',
	() => 'Easy does it today. Showing up is already the win.'
];

const LAST_OF_WEEK = [
	'This one completes your week. Finish strong.',
	'Last session of the week. Make it a good one.',
	'One more and the weekly goal is done.'
];

const FIRST_OF_WEEK = [
	'First workout of the week. Get the week started right.',
	'A fresh week, a fresh start. One session at a time.',
	'The first one sets the tone for the week.'
];

const REGULAR = [
	(n: number) => `Workout number ${n} this week. The consistency shows.`,
	() => 'Warm up, focus, and enjoy it. You have done this before.',
	() => 'Quality reps, one set at a time.',
	() => 'You do not have to be perfect, just present.',
	(n: number) => `That makes ${n} this week. Keep stacking them.`,
	() => 'Future you will be glad you went.'
];

export function startMessage(ctx: StartContext): Greeting {
	const step = dayNumber(ctx.date);
	const headline = rotate(START_HEADLINES[dayPart(ctx.hour)], step, 'workout-start-headline');
	const thisOne = ctx.workoutsThisWeek + 1;

	let line: string;
	if (ctx.totalWorkouts === 0) {
		line = rotate(FIRST_EVER, step, 'workout-first');
	} else if (ctx.daysSinceLast !== null && ctx.daysSinceLast >= 10) {
		line = rotate(COMEBACK, step, 'workout-comeback')(ctx.daysSinceLast);
	} else if (ctx.weeklyTarget !== null && thisOne === ctx.weeklyTarget && ctx.weeklyTarget > 1) {
		line = rotate(LAST_OF_WEEK, step, 'workout-last');
	} else if (ctx.workoutsThisWeek === 0) {
		line = rotate(FIRST_OF_WEEK, step, 'workout-first-of-week');
	} else {
		line = rotate(REGULAR, step, 'workout-regular')(thisOne);
	}
	return { headline, line };
}

// ---------------------------------------------------------------- during

/** A short encouraging line for the header while training, from how much of the plan is done. */
export function progressLine(done: number, total: number, date: string): string {
	const step = dayNumber(date);
	if (total === 0) return rotate(['Add exercises as you go.', 'Log what you do, it all counts.'], step, 'workout-free');
	if (done === 0) return rotate(['Start with the first set. The rest follows.', 'Warm up, then the first set.'], step, 'workout-zero');
	if (done >= total) return rotate(['Everything planned is done. Finish when you are ready.', 'Plan complete. Strong work.'], step, 'workout-all');
	if (done * 2 >= total) return rotate(['Over halfway. Keep going.', 'More than half done. The hard part is behind you.'], step, 'workout-half');
	return rotate(['Getting into it. Nice rhythm.', 'Good start. Keep the pace.'], step, 'workout-early');
}

// ---------------------------------------------------------------- finish

export interface FinishContext {
	date: string;
	minutes: number;
	sets: number;
	personalBests: number;
	workoutsThisWeek: number;
	weeklyTarget: number | null;
	autoEnded: boolean;
}

const FINISH_HEADLINES = ['Workout done', 'Nice work', 'That is a wrap', 'Done and dusted', 'Another one in the books'];

const FINISH_LINES = [
	(c: FinishContext) => `${minutesText(c.minutes)} and ${c.sets} sets. Time well spent.`,
	(c: FinishContext) => `${c.sets} sets in ${minutesText(c.minutes)}. Enjoy the rest of your day.`,
	() => 'Rest, eat, recover. The progress happens now.',
	() => 'You showed up and did the work. That is what counts.'
];

const PERSONAL_BEST_HEADLINES = ['New personal best!', 'Stronger than ever', 'A new record. Look at you'];

export function finishMessage(ctx: FinishContext): Greeting {
	const step = dayNumber(ctx.date);

	if (ctx.autoEnded) {
		return {
			headline: 'Saved for you',
			line: `The workout was still running, so it was ended automatically at ${minutesText(ctx.minutes)}. Check that this looks right.`
		};
	}

	const goalReached = ctx.weeklyTarget !== null && ctx.workoutsThisWeek >= ctx.weeklyTarget && ctx.weeklyTarget > 1;
	if (ctx.personalBests > 0) {
		return {
			headline: rotate(PERSONAL_BEST_HEADLINES, step, 'workout-pb'),
			line: goalReached
				? `And that is your weekly goal done (${ctx.workoutsThisWeek} of ${ctx.weeklyTarget}). What a week.`
				: ctx.personalBests === 1
					? 'One personal best today. This is what progress looks like.'
					: `${ctx.personalBests} personal bests today. This is what progress looks like.`
		};
	}
	if (goalReached) {
		return {
			headline: 'Weekly goal reached',
			line: `${ctx.workoutsThisWeek} of ${ctx.weeklyTarget} workouts done. Anything extra is a bonus.`
		};
	}
	return {
		headline: rotate(FINISH_HEADLINES, step, 'workout-finish-headline'),
		line: rotate(FINISH_LINES, step, 'workout-finish-line')(ctx)
	};
}
