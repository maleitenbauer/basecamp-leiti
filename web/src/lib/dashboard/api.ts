import { http } from '$lib/api/http';
import type { Layout } from './layout';

export const dashboardApi = {
	layout: () => http<Layout>('/api/dashboard/layout'),
	saveLayout: (layout: Layout) =>
		http<Layout>('/api/dashboard/layout', { method: 'PUT', body: JSON.stringify(layout) })
};
