import { http } from '$lib/api/http';
import type {
	AddShoppingItem,
	AddTemplateItem,
	CreateShoppingList,
	CreateTemplate,
	KnownItem,
	MoveItems,
	ShoppingItem,
	ShoppingList,
	ShoppingListsResponse,
	Template,
	TemplateItem,
	UpdateShoppingItem,
	UpdateShoppingList,
	UseTemplate
} from './types';

const base = '/api/logbook/shopping';

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });

export const shoppingApi = {
	listAll: () => http<ShoppingListsResponse>(`${base}/lists`),
	knownItems: () => http<KnownItem[]>(`${base}/known-items`),

	createList: (body: CreateShoppingList) => http<ShoppingList>(`${base}/lists`, json('POST', body)),
	updateList: (id: number, body: UpdateShoppingList) => http<ShoppingList>(`${base}/lists/${id}`, json('PATCH', body)),
	deleteList: (id: number) => http<void>(`${base}/lists/${id}`, { method: 'DELETE' }),

	addItem: (listId: number, body: AddShoppingItem) =>
		http<ShoppingItem>(`${base}/lists/${listId}/items`, json('POST', body)),
	updateItem: (id: number, body: UpdateShoppingItem) => http<ShoppingItem>(`${base}/items/${id}`, json('PATCH', body)),
	deleteItem: (id: number) => http<void>(`${base}/items/${id}`, { method: 'DELETE' }),
	moveItem: (id: number, body: MoveItems) => http<ShoppingItem>(`${base}/items/${id}/move`, json('POST', body)),
	moveUnchecked: (listId: number, body: MoveItems) =>
		http<ShoppingItem[]>(`${base}/lists/${listId}/move-unchecked`, json('POST', body)),

	saveListAsTemplate: (listId: number, body: CreateTemplate) =>
		http<Template>(`${base}/lists/${listId}/save-as-template`, json('POST', body)),

	listTemplates: () => http<Template[]>(`${base}/templates`),
	createTemplate: (body: CreateTemplate) => http<Template>(`${base}/templates`, json('POST', body)),
	renameTemplate: (id: number, name: string) => http<Template>(`${base}/templates/${id}`, json('PATCH', { name })),
	deleteTemplate: (id: number) => http<void>(`${base}/templates/${id}`, { method: 'DELETE' }),
	addTemplateItem: (templateId: number, body: AddTemplateItem) =>
		http<TemplateItem>(`${base}/templates/${templateId}/items`, json('POST', body)),
	removeTemplateItem: (id: number) => http<void>(`${base}/template-items/${id}`, { method: 'DELETE' }),
	useTemplate: (templateId: number, body: UseTemplate) =>
		http<ShoppingList>(`${base}/templates/${templateId}/use`, json('POST', body))
};
