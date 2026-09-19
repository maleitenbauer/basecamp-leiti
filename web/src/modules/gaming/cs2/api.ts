import { http } from '$lib/api/http';
import type {
	CreatePrinciple,
	CreateReview,
	CreateRoutineItem,
	Principle,
	Review,
	Reviews,
	RoutineDay,
	UpdatePrinciple,
	UpdateRoutineItem
} from './types';

const base = '/api/gaming/cs2/improvement';

const json = (method: string, body: unknown): RequestInit => ({
	method,
	body: JSON.stringify(body)
});

export const cs2Api = {
	routine: (date: string) => http<RoutineDay>(`${base}/routine?date=${date}`),
	createItem: (body: CreateRoutineItem) => http<unknown>(`${base}/routine/items`, json('POST', body)),
	updateItem: (id: number, body: UpdateRoutineItem) =>
		http<unknown>(`${base}/routine/items/${id}`, json('PATCH', body)),
	deleteItem: (id: number) => http<void>(`${base}/routine/items/${id}`, { method: 'DELETE' }),
	setDone: (id: number, date: string, body: { done: boolean; minutes?: number }) =>
		http<void>(`${base}/routine/items/${id}/days/${date}`, json('PUT', body)),

	reviews: () => http<Reviews>(`${base}/reviews`),
	createReview: (body: CreateReview) => http<Review>(`${base}/reviews`, json('POST', body)),
	deleteReview: (id: number) => http<void>(`${base}/reviews/${id}`, { method: 'DELETE' }),

	principles: () => http<Principle[]>(`${base}/principles`),
	createPrinciple: (body: CreatePrinciple) =>
		http<Principle>(`${base}/principles`, json('POST', body)),
	updatePrinciple: (id: number, body: UpdatePrinciple) =>
		http<Principle>(`${base}/principles/${id}`, json('PATCH', body)),
	deletePrinciple: (id: number) => http<void>(`${base}/principles/${id}`, { method: 'DELETE' })
};
