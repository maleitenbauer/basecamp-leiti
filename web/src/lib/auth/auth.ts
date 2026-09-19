import { ApiError, ensureCsrfToken } from '$lib/api/http';

export interface Me {
	id: number;
	username: string;
	role: 'ADMIN' | 'USER';
}

/** Returns the signed-in user, or null when there is no valid session. */
export async function fetchMe(f: typeof fetch = fetch): Promise<Me | null> {
	const res = await f('/api/auth/me');
	if (res.status === 401) return null;
	if (!res.ok) throw new ApiError(res.status, `Backend error (HTTP ${res.status})`);
	return (await res.json()) as Me;
}

export async function login(username: string, password: string): Promise<void> {
	const res = await fetch('/api/auth/login', {
		method: 'POST',
		headers: {
			'Content-Type': 'application/x-www-form-urlencoded',
			'X-XSRF-TOKEN': await ensureCsrfToken()
		},
		body: new URLSearchParams({ username, password })
	});
	if (res.status === 401) throw new Error('Wrong username or password, or the account is temporarily locked.');
	if (!res.ok) throw new Error(`Login failed (HTTP ${res.status})`);
}

export async function logout(): Promise<void> {
	await fetch('/api/auth/logout', {
		method: 'POST',
		headers: { 'X-XSRF-TOKEN': await ensureCsrfToken() }
	});
}
