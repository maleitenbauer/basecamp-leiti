import { defineWidget } from '$lib/dashboard/types';
import { useRoutineProgress } from './progress';
import RoutineWidget from './RoutineWidget.svelte';

export default defineWidget({
	id: 'lifestyle.routine',
	title: 'Routines',
	moduleId: 'lifestyle',
	href: '/lifestyle/routine',
	order: 10,
	component: RoutineWidget,
	progress: useRoutineProgress
});
