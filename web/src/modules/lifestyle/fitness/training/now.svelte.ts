/** A clock that ticks, for timers: read `.value` (milliseconds) in a template or $derived and it updates by itself. */
export function useNow(intervalMs = 1000) {
	let value = $state(Date.now());
	$effect(() => {
		value = Date.now();
		const timer = setInterval(() => (value = Date.now()), intervalMs);
		return () => clearInterval(timer);
	});
	return {
		get value() {
			return value;
		}
	};
}
