export class ApiError extends Error {
	constructor(
		public status: number,
		message: string
	) {
		super(message);
	}
}

/** The API sets an XSRF-TOKEN cookie; state-changing requests must echo it in X-XSRF-TOKEN. */
export function csrfToken(): string | undefined {
	const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
	return match ? decodeURIComponent(match[1]) : undefined;
}

/** Makes sure the CSRF cookie exists (the API writes it on any response). */
export async function ensureCsrfToken(): Promise<string> {
	if (!csrfToken()) await fetch('/api/auth/me');
	return csrfToken() ?? '';
}

/** Small typed fetch wrapper for the backend; throws ApiError with the server's problem detail. */
export async function http<T>(path: string, init: RequestInit = {}): Promise<T> {
	const method = (init.method ?? 'GET').toUpperCase();
	const headers = new Headers(init.headers);
	if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
	if (method !== 'GET' && method !== 'HEAD') headers.set('X-XSRF-TOKEN', await ensureCsrfToken());

	const res = await fetch(path, { ...init, headers });

	if (res.status === 401 && location.pathname !== '/login') {
		// session expired or revoked
		location.assign('/login');
		throw new ApiError(401, 'Signed out');
	}
	if (!res.ok) {
		let detail = res.statusText || `HTTP ${res.status}`;
		try {
			detail = (await res.json()).detail ?? detail;
		} catch {
			// no JSON body
		}
		throw new ApiError(res.status, detail);
	}
	return res.status === 204 ? (undefined as T) : ((await res.json()) as T);
}
