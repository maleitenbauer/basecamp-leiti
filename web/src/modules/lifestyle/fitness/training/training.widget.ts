import { defineWidget } from '$lib/dashboard/types';
import TrainingWidget from './TrainingWidget.svelte';

export default defineWidget({
	id: 'lifestyle.fitness.training',
	title: 'Training',
	moduleId: 'lifestyle',
	href: '/lifestyle/fitness/training',
	order: 35,
	component: TrainingWidget
});
