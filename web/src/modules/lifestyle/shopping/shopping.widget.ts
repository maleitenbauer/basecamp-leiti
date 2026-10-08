import { defineWidget } from '$lib/dashboard/types';
import ShoppingWidget from './ShoppingWidget.svelte';

export default defineWidget({
	id: 'lifestyle.shopping',
	title: 'Shopping',
	moduleId: 'lifestyle',
	href: '/lifestyle/shopping',
	order: 40,
	component: ShoppingWidget
});
