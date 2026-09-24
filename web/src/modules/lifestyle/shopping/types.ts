export interface ShoppingItem {
	id: number;
	listId: number;
	name: string;
	quantity: number | null;
	unit: string | null;
	checked: boolean;
}

export interface ShoppingList {
	id: number;
	name: string;
	finalizedAt: string | null;
	items: ShoppingItem[];
	updatedAt: string;
}

export interface ShoppingListsResponse {
	active: ShoppingList[];
	finalized: ShoppingList[];
}

export interface KnownItem {
	name: string;
	useCount: number;
}

export interface CreateShoppingList {
	name: string;
}

export interface UpdateShoppingList {
	name?: string;
	finalized?: boolean;
}

export interface AddShoppingItem {
	name: string;
	quantity?: number;
	unit?: string;
}

export interface UpdateShoppingItem {
	name?: string;
	quantity?: number;
	clearQuantity?: boolean;
	unit?: string;
	clearUnit?: boolean;
	checked?: boolean;
}

export interface MoveItems {
	targetListId?: number;
	newListName?: string;
}

export function itemLabel(item: Pick<ShoppingItem, 'quantity' | 'unit'>): string {
	if (!item.quantity) return '';
	return item.unit ? `${item.quantity} ${item.unit}` : `${item.quantity}×`;
}

// ---- recurring templates ----

export interface TemplateItem {
	id: number;
	name: string;
	quantity: number | null;
	unit: string | null;
}

export interface Template {
	id: number;
	name: string;
	items: TemplateItem[];
	updatedAt: string;
}

export interface CreateTemplate {
	name: string;
}

export interface AddTemplateItem {
	name: string;
	quantity?: number;
	unit?: string;
}

export interface UseTemplate {
	name?: string;
}
