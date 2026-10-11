import { defineWidget } from '$lib/dashboard/types';
import ReportWidget from './ReportWidget.svelte';

export default defineWidget({
	id: 'report.weekly',
	title: 'Weekly report',
	moduleId: 'report',
	href: '/report',
	order: 60,
	component: ReportWidget
});
