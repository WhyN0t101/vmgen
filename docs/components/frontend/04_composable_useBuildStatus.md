# Composable — `useBuildStatus.ts`

## What it does

When you submit a build and land on the build status page, something needs to keep
asking the backend "is it done yet?" and update the UI when the state changes.
That is this composable.

---

## TanStack Query — what it is

TanStack Query is a data-fetching library. Instead of manually writing polling logic:

```ts
// without TanStack Query — manual approach
const data = ref(null)
let interval: number | null = null

onMounted(async () => {
  data.value = await api.getBuild(id)

  interval = setInterval(async () => {
    data.value = await api.getBuild(id)

    if (data.value.state === 'ready' || data.value.state === 'failed') {
      clearInterval(interval)
    }
  }, 2000)
})

onUnmounted(() => {
  clearInterval(interval) // must remember this or it keeps running after navigation
})
```

You declare what you want and TanStack Query handles everything:

```ts
// with TanStack Query
return useQuery({
  queryKey: ['build', requestId],
  queryFn: () => api.getBuild(requestId),
  refetchInterval: (query) => {
    const state = query.state.data?.state
    if (state === 'building' || state === 'queued' || state === 'exporting') return 2000
    return false
  }
})
```

TanStack Query handles:
- Initial fetch on mount
- Polling interval management
- Stopping the poll when done
- Caching (two components reading the same build share one request)
- Cleanup when the component unmounts (no memory leaks)

---

## The code explained

```ts
export function useBuildStatus(requestId: MaybeRefOrGetter<string>) {
  const requestIdValue = computed(() => toValue(requestId))
  const queryKey = computed(() => ['build', requestIdValue.value])
```

`MaybeRefOrGetter<string>` means the caller can pass a plain string, a `ref('')`, or a
getter function. `toValue()` unwraps any of those into a plain string. This makes the
composable flexible.

`queryKey` is TanStack Query's cache identifier. `['build', 'abc-123']` means "the build
with id abc-123". If two components call `useBuildStatus('abc-123')`, they share the same
cache entry — only one HTTP request fires.

```ts
refetchInterval: (query) => {
  const data = query.state.data
  if (data?.state === 'building' || data?.state === 'queued' || data?.state === 'exporting') {
    return 2000
  }
  return false
}
```

Called by TanStack Query after every fetch to decide when to fetch again.
It receives the current query state so you can inspect the last response:
- Build still in progress → return `2000` (poll again in 2 seconds)
- Build `READY` or `FAILED` → return `false` (stop polling)

Polling is self-terminating — the build state drives it, no manual cleanup needed.

---

## Concrete data flow

```
1. User submits build on GeneratePage
   → api.createBuild({ templateId, scripts })
   → backend returns { requestId: 'abc-123', state: 'QUEUED' }
   → router.push('/builds/abc-123')

2. BuildStatusPage mounts
   → const { data, isLoading } = useBuildStatus('abc-123')
   → TanStack Query fires immediately

3. First fetch
   → api.getBuild('abc-123')
   → apiRequest('/api/builds/abc-123') + JWT header
   → backend returns { state: 'QUEUED' }
   → data.value = { state: 'QUEUED' }
   → component shows "QUEUED"
   → refetchInterval returns 2000

4. 2 seconds later
   → same chain → backend returns { state: 'BUILDING' }
   → component shows "BUILDING"
   → refetchInterval returns 2000

5. 2 seconds later
   → same chain → backend returns { state: 'EXPORTING' }
   → component shows "EXPORTING"
   → refetchInterval returns 2000

6. 2 seconds later
   → same chain → backend returns { state: 'READY' }
   → component shows download button
   → refetchInterval returns false → polling stops
```

The component never calls `fetch`, never sets a timer, never clears anything.
It just reads `data.value` and renders. All polling logic lives in the composable.

---

## Why a composable instead of logic in the component

The composable separates the "how do I get this data" from the "how do I display this data."

The BuildStatusPage only needs to know:
- `data` — the current build object
- `isLoading` — whether the first fetch is still in progress

It does not need to know that polling happens every 2 seconds, or that it stops on READY/FAILED.
That detail lives in `useBuildStatus` and can be changed without touching the page component.

---

## Analogy — BroadcastChannel vs WebSocket

`useBuildStatus` uses polling (repeated requests). A WebSocket would be an alternative:
instead of the frontend asking "are you done?" every 2 seconds, the backend would push
"build is ready" when it happens. Polling is simpler to implement and perfectly fine at
this scale, which is why TanStack Query was used instead of a WebSocket connection.
