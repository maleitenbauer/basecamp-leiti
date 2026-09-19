<script lang="ts">
	import '../app.css';
	import { QueryClient, QueryClientProvider } from '@tanstack/svelte-query';

	let { children } = $props();
	const queryClient = new QueryClient();
</script>

<QueryClientProvider client={queryClient}>
	<div class="min-h-screen bg-slate-900 text-slate-100">
		{@render children()}
	</div>
</QueryClientProvider>
