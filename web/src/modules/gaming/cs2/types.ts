export type RoutineCategory = 'WARMUP' | 'PRACTICE';
export type PrincipleCategory =
	| 'MINDSET'
	| 'MOVEMENT'
	| 'UTILITY'
	| 'AIM'
	| 'PRACTICE'
	| 'PREPARATION';

export const ROUTINE_CATEGORIES: { value: RoutineCategory; label: string }[] = [
	{ value: 'WARMUP', label: 'Warmup' },
	{ value: 'PRACTICE', label: 'Practice' }
];

export const PRINCIPLE_CATEGORIES: { value: PrincipleCategory; label: string }[] = [
	{ value: 'MINDSET', label: 'Mindset' },
	{ value: 'MOVEMENT', label: 'Movement' },
	{ value: 'UTILITY', label: 'Utility' },
	{ value: 'AIM', label: 'Aim' },
	{ value: 'PRACTICE', label: 'Practice' },
	{ value: 'PREPARATION', label: 'Preparation' }
];

export const categoryLabel = (value: string): string =>
	[...ROUTINE_CATEGORIES, ...PRINCIPLE_CATEGORIES].find((c) => c.value === value)?.label ?? value;

export interface RoutineItem {
	id: number;
	title: string;
	description: string | null;
	category: RoutineCategory;
	targetMinutes: number | null;
	active: boolean;
	done: boolean;
	minutes: number | null;
}

export interface DaySummary {
	date: string;
	doneCount: number;
}

export interface RoutineDay {
	date: string;
	items: RoutineItem[];
	streak: number;
	history: DaySummary[];
}

export interface CreateRoutineItem {
	title: string;
	description?: string;
	category: RoutineCategory;
	targetMinutes?: number;
}

export interface UpdateRoutineItem {
	title?: string;
	description?: string;
	category?: RoutineCategory;
	targetMinutes?: number;
	active?: boolean;
}

export interface Review {
	id: number;
	playedOn: string;
	focus: number;
	movement: number;
	utility: number;
	notes: string | null;
}

export interface ReviewAverages {
	focus: number;
	movement: number;
	utility: number;
	count: number;
}

export interface Reviews {
	reviews: Review[];
	averages: ReviewAverages | null;
}

export interface CreateReview {
	playedOn?: string;
	focus: number;
	movement: number;
	utility: number;
	notes?: string;
}

export interface Principle {
	id: number;
	title: string;
	body: string | null;
	category: PrincipleCategory;
	pinned: boolean;
}

export interface CreatePrinciple {
	title: string;
	body?: string;
	category: PrincipleCategory;
}

export interface UpdatePrinciple {
	title?: string;
	body?: string;
	category?: PrincipleCategory;
	pinned?: boolean;
}
