/** RECURRING is due on specific days like a calendar event; TIMES_PER_WEEK is "N times a week, on whichever days". */
export type ScheduleType = 'RECURRING' | 'TIMES_PER_WEEK';
export type RecurUnit = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR';
export type MonthMode = 'DAY_OF_MONTH' | 'LAST_DAY' | 'NTH_WEEKDAY';
export type EndType = 'NEVER' | 'ON_DATE' | 'AFTER_COUNT';
/** OPEN needs doing; DONE was done that day (or this week's target is met); NOT_DUE just isn't scheduled that day. */
export type RoutineState = 'OPEN' | 'DONE' | 'NOT_DUE';

/** The saved rule with every default filled in. Weekdays are ISO: 1 = Monday ... 7 = Sunday. */
export interface Recurrence {
	unit: RecurUnit;
	interval: number;
	weekdays: number[];
	monthMode: MonthMode;
	monthDay: number;
	/** 1-4, or 5 for "the last" */
	nth: number;
	weekday: number;
	yearMonth: number;
	startDate: string;
	endType: EndType;
	endDate: string | null;
	endCount: number | null;
}

export interface Routine {
	id: number;
	name: string;
	note: string | null;
	scheduleType: ScheduleType;
	/** Only meaningful for TIMES_PER_WEEK. */
	timesPerWeek: number;
	/** Null for routines that are simply checked off rather than timed. */
	targetMinutes: number | null;
	carryOver: boolean;
	/** e.g. "Every 2 weeks on Mon, Wed" */
	summary: string;
	recurrence: Recurrence | null;
	state: RoutineState;
	open: boolean;
	doneToday: boolean;
	minutesToday: number | null;
	/** YYYY-MM-DD days of the Monday-Sunday week around the viewed date on which it was done. */
	doneDaysThisWeek: string[];
	/** A scheduled routine's occurrence falls on the viewed day. */
	dueToday: boolean;
	/** When a scheduled routine isn't due on the viewed day: its next occurrence. */
	nextDue: string | null;
	/** For a carry-over routine that's open from an earlier occurrence: the day that occurrence was due. */
	overdueSince: string | null;
}

export interface RoutineOverview {
	date: string;
	weekStart: string;
	routines: Routine[];
	openCount: number;
}

export interface RecurrenceInput {
	unit: RecurUnit;
	interval: number;
	weekdays: number[];
	monthMode: MonthMode;
	monthDay?: number;
	nth?: number;
	weekday?: number;
	yearMonth?: number;
	startDate: string;
	endType: EndType;
	endDate?: string;
	endCount?: number;
}

export interface RoutineInput {
	name: string;
	note: string | null;
	scheduleType: ScheduleType;
	timesPerWeek: number;
	recurrence: RecurrenceInput | null;
	carryOver: boolean;
	targetMinutes: number | null;
}

export interface ReminderSettings {
	enabled: boolean;
	/** HH:mm in the user's time zone */
	remindAt: string;
	timezone: string;
}

export interface SaveReminder {
	enabled: boolean;
	remindAt: string;
	timezone?: string;
}

export interface SendNowResult {
	sent: boolean;
	message: string;
}

export const WEEKDAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
export const MONTHS = [
	'January',
	'February',
	'March',
	'April',
	'May',
	'June',
	'July',
	'August',
	'September',
	'October',
	'November',
	'December'
];
export const ORDINALS = ['first', 'second', 'third', 'fourth'];

/** The calendar facts about a YYYY-MM-DD date that a repeat menu offers choices from. */
export function dateFacts(iso: string) {
	const [year, month, day] = iso.split('-').map(Number);
	const jsDay = new Date(year, month - 1, day).getDay(); // 0 = Sunday
	const daysInMonth = new Date(year, month, 0).getDate();
	return {
		day,
		month,
		/** ISO weekday: 1 = Monday ... 7 = Sunday */
		weekday: ((jsDay + 6) % 7) + 1,
		/** which occurrence of its weekday it is in the month: 1-5 */
		nth: Math.floor((day - 1) / 7) + 1,
		daysInMonth,
		/** the last occurrence of its weekday in the month */
		isLastOfWeekday: day + 7 > daysInMonth
	};
}
