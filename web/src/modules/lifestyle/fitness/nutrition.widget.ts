import { defineWidget } from '$lib/dashboard/types';
import NutritionWidget from './NutritionWidget.svelte';

export default defineWidget({
	id: 'lifestyle.fitness.nutrition',
	title: 'Calories today',
	moduleId: 'lifestyle',
	href: '/lifestyle/fitness',
	order: 30,
	component: NutritionWidget
});
