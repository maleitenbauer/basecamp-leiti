import { defineWidget } from '$lib/dashboard/types';
import { useTodosProgress } from './progress';
import TodosWidget from './TodosWidget.svelte';

export default defineWidget({
	id: 'lifestyle.todos',
	title: 'Todos',
	moduleId: 'lifestyle',
	href: '/lifestyle/todos',
	order: 20,
	component: TodosWidget,
	progress: useTodosProgress
});
