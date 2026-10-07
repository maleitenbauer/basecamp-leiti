import { http } from '$lib/api/http';
import type { ReminderSettings, RoutineInput, RoutineOverview, SaveReminder, SendNowResult } from './types';

const base = '/api/logbook/routines';

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });

export const routineApi = {
	overview: (date: string) => http<RoutineOverview>(`${base}?date=${date}`),
	create: (body: RoutineInput) => http<{ id: number }>(base, json('POST', body)),
	update: (id: number, body: RoutineInput) => http<void>(`${base}/${id}`, json('PUT', body)),
	remove: (id: number) => http<void>(`${base}/${id}`, { method: 'DELETE' }),

	/** Marks it done on that day; for a timed routine, minutes defaults to its target when omitted. */
	markDone: (id: number, date: string, minutes?: number) =>
		http<void>(`${base}/${id}/completions/${date}`, json('PUT', minutes ? { minutes } : {})),
	undo: (id: number, date: string) => http<void>(`${base}/${id}/completions/${date}`, { method: 'DELETE' }),

	reminder: () => http<ReminderSettings>(`${base}/reminder`),
	saveReminder: (body: SaveReminder) => http<ReminderSettings>(`${base}/reminder`, json('PUT', body)),
	sendNow: () => http<SendNowResult>(`${base}/reminder/send-now`, { method: 'POST' })
};
