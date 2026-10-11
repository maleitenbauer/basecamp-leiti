import { http } from '$lib/api/http';
import type { WeeklyReport } from './types';

export const reportApi = {
	/** The report for the Monday-to-Sunday week containing [weekStart]; [today] is the browser's today. */
	weekly: (weekStart: string, today: string) =>
		http<WeeklyReport>(`/api/reports/weekly?weekStart=${weekStart}&today=${today}`)
};
