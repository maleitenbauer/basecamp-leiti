/**
 * Every frontend module registers itself here. The navigation, the dashboard and the submodule tabs are all
 * built from this tree:  module -> submodules -> pages (tabs).
 * To add a submodule: add an entry below and create the routes under src/routes/<module>/<submodule>/.
 */
export interface PageInfo {
	id: string;
	name: string;
	href: string;
}

export interface SubmoduleInfo {
	id: string;
	name: string;
	href: string;
	description: string;
	pages: PageInfo[];
}

export interface ModuleInfo {
	id: string;
	name: string;
	href: string;
	icon: string;
	description: string;
	submodules: SubmoduleInfo[];
}

export const modules: ModuleInfo[] = [
	{
		id: 'gaming',
		name: 'Gaming',
		href: '/gaming',
		icon: '🎮',
		description: 'Games, training and improvement',
		submodules: [
			{
				id: 'cs2',
				name: 'Counter-Strike 2',
				href: '/gaming/cs2',
				description: 'Daily routine, session reviews and coach principles',
				pages: [
					{ id: 'overview', name: 'Overview', href: '/gaming/cs2' },
					{ id: 'matches', name: 'Matches', href: '/gaming/cs2/matches' },
					{ id: 'improvement', name: 'Improvement', href: '/gaming/cs2/improvement' }
				]
			}
		]
	}
];

export function findModule(moduleId: string): ModuleInfo | undefined {
	return modules.find((m) => m.id === moduleId);
}

export function findSubmodule(moduleId: string, submoduleId: string) {
	const module = findModule(moduleId);
	const submodule = module?.submodules.find((s) => s.id === submoduleId);
	return module && submodule ? { module, submodule } : undefined;
}
