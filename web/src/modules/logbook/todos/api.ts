import { http } from '$lib/api/http';
import type { CreateTodo, ReminderSettings, SendNowResult, Todo, TodoList, UpdateTodo } from './types';

const base = '/api/logbook/todos';

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });

export const todosApi = {
	list: () => http<TodoList>(base),
	create: (body: CreateTodo) => http<Todo>(base, json('POST', body)),
	update: (id: number, body: UpdateTodo) => http<Todo>(`${base}/${id}`, json('PATCH', body)),
	remove: (id: number) => http<void>(`${base}/${id}`, { method: 'DELETE' }),

	reminders: () => http<ReminderSettings>(`${base}/reminders`),
	saveReminders: (body: ReminderSettings) => http<ReminderSettings>(`${base}/reminders`, json('PUT', body)),
	sendNow: () => http<SendNowResult>(`${base}/reminders/send-now`, { method: 'POST' })
};
