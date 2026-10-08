import { defineWidget } from '$lib/dashboard/types';
import Cs2Widget from './Cs2Widget.svelte';
import { useCs2Progress } from './progress';

export default defineWidget({
	id: 'gaming.cs2.practice',
	title: 'CS2 practice',
	moduleId: 'gaming',
	href: '/gaming/cs2/improvement',
	order: 50,
	component: Cs2Widget,
	progress: useCs2Progress
});
