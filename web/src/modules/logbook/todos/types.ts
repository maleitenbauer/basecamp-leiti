import { daysBetween } from '$lib/dates';

export interface Todo {
	id: number;
	title: string;
	note: string | null;
	dueDate: string | null;
	done: boolean;
	doneAt: string | null;
	createdAt: string;
}

export interface TodoList {
	open: Todo[];
	done: Todo[];
	doneTotal: number;
}

export interface CreateTodo {
	title: string;
	dueDate?: string;
	note?: string;
}

export interface UpdateTodo {
	title?: string;
	dueDate?: string;
	clearDueDate?: boolean;
	note?: string;
	done?: boolean;
}

export interface ReminderSettings {
	enabled: boolean;
	remindAt: string;
	notifyDueToday: boolean;
	notifyOverdue: boolean;
	timezone: string;
}

export interface SendNowResult {
	sent: boolean;
	message: string;
}

export type DueGroup = 'overdue' | 'today' | 'upcoming' | 'none';

export function dueGroup(dueDate: string | null, today: string): DueGroup {
	if (!dueDate) return 'none';
	const diff = daysBetween(today, dueDate);
	return diff < 0 ? 'overdue' : diff === 0 ? 'today' : 'upcoming';
}

/** Short deadline text and colour, relative to today. */
export function dueBadge(dueDate: string, today: string): { text: string; tone: string } {
	const diff = daysBetween(today, dueDate);
	if (diff < 0) {
		return { text: diff === -1 ? '1 day overdue' : `${-diff} days overdue`, tone: 'bg-red-900/60 text-red-300' };
	}
	if (diff === 0) return { text: 'Today', tone: 'bg-brand-500 text-slate-950' };
	if (diff === 1) return { text: 'Tomorrow', tone: 'bg-slate-700 text-slate-200' };
	const date = new Date(`${dueDate}T12:00:00`);
	const text =
		diff < 7
			? date.toLocaleDateString(undefined, { weekday: 'long' })
			: date.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
	return { text, tone: 'bg-slate-800 text-slate-300' };
}
