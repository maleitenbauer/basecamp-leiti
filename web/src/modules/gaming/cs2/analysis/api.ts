import { http } from '$lib/api/http';
import type { DemoAnalysis, DemoSummary } from './types';

const base = '/api/gaming/cs2/analysis';

export const analysisApi = {
	available: () => http<{ available: boolean }>(`${base}/available`),
	listDemos: () => http<DemoSummary[]>(`${base}/demos`),
	getAnalysis: (id: number) => http<DemoAnalysis>(`${base}/demos/${id}`),
	retry: (id: number) => http<DemoSummary>(`${base}/demos/${id}/retry`, { method: 'POST' }),
	deleteDemo: (id: number) => http<void>(`${base}/demos/${id}`, { method: 'DELETE' }),

	uploadDemo: (file: File) => {
		const body = new FormData();
		body.append('file', file);
		return http<DemoSummary>(`${base}/demos`, { method: 'POST', body });
	}
};
