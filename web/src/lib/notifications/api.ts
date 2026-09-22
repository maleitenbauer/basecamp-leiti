import { http } from '$lib/api/http';

export interface NotificationItem {
	id: number;
	title: string;
	body: string | null;
	url: string | null;
	category: string;
	createdAt: string;
	read: boolean;
}

export interface NotificationsResponse {
	unread: number;
	items: NotificationItem[];
}

export interface Device {
	id: number;
	label: string;
	endpoint: string;
	createdAt: string;
	lastUsedAt: string | null;
}

export interface NotificationConfig {
	pushAvailable: boolean;
	/** set when the server's push keys are misconfigured; explains what is wrong */
	pushProblem: string | null;
	vapidPublicKey: string | null;
	timezone: string;
	devices: Device[];
}

export interface DeviceDelivery {
	device: string;
	delivered: boolean;
	detail: string;
}

export interface TestResult {
	pushAvailable: boolean;
	deviceCount: number;
	deliveries: DeviceDelivery[];
}

export const notificationsApi = {
	list: () => http<NotificationsResponse>('/api/notifications'),
	readAll: () => http<void>('/api/notifications/read-all', { method: 'POST' }),
	read: (id: number) => http<void>(`/api/notifications/${id}/read`, { method: 'POST' }),
	config: () => http<NotificationConfig>('/api/notifications/config'),
	setTimezone: (timezone: string) =>
		http<void>('/api/notifications/settings', { method: 'PUT', body: JSON.stringify({ timezone }) }),
	subscribe: (body: { endpoint: string; p256dh: string; auth: string; userAgent: string }) =>
		http<void>('/api/notifications/subscriptions', { method: 'POST', body: JSON.stringify(body) }),
	unsubscribe: (id: number) => http<void>(`/api/notifications/subscriptions/${id}`, { method: 'DELETE' }),
	test: () => http<TestResult>('/api/notifications/test', { method: 'POST' })
};

/** The time zone this browser is in, for example Europe/Vienna. */
export const browserTimezone = (): string => Intl.DateTimeFormat().resolvedOptions().timeZone;
