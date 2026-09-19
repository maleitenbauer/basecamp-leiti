import { redirect } from '@sveltejs/kit';
import { fetchMe } from '$lib/auth/auth';
import type { LayoutLoad } from './$types';

export const ssr = false;
export const prerender = false;

/** Route guard: everything except /login needs a valid session. */
export const load: LayoutLoad = async ({ fetch, url }) => {
	const user = await fetchMe(fetch);
	if (!user && url.pathname !== '/login') redirect(307, '/login');
	if (user && url.pathname === '/login') redirect(307, '/');
	return { user };
};
