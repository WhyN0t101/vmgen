# API Client — `src/api/client.ts`

## What this file is

Every single HTTP request the frontend makes goes through this file.
It is the only place `fetch()` is called. Think of it as the gateway between
the frontend and the backend.

No component ever calls `fetch()` directly. The chain is always:

```
Component → composable → endpoints.ts → apiRequest() → fetch()
```

---

## The base URL

```ts
const _configuredUrl = import.meta.env.VITE_API_URL
export const API_BASE: string = _configuredUrl || 'http://localhost:8080'
```

`import.meta.env.VITE_API_URL` reads an environment variable set at build time.
In production this would be set to the actual server URL.
If not set, it falls back to `localhost:8080` for local development.

---

## Global error handlers

```ts
let onUnauthorized: ((message: string) => void) | null = null
let onForbidden:    ((message: string) => void) | null = null

export function setUnauthorizedHandler(handler) { onUnauthorized = handler }
export function setForbiddenHandler(handler)    { onForbidden = handler }
```

These are callbacks that `App.vue` registers at startup. When a 401 or 403 happens
anywhere in the app, the client calls these. This keeps the "what do we do on auth failure"
logic in one place instead of every component handling it separately.

---

## `rawRequest()` — unauthenticated requests

```ts
export async function rawRequest<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const headers = { 'Content-Type': 'application/json', ...options.headers }
  const response = await fetch(`${API_BASE}${endpoint}`, { ...options, headers })

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw buildError(response.status, response.statusText, errorData)
  }

  if (!hasBody(response)) return undefined as T
  return response.json()
}
```

Plain HTTP request — no token injected. Used for:
- `POST /api/auth/login` — no token exists yet
- `POST /api/auth/register` — same reason
- `POST /api/auth/logout` — token might already be invalid

`!response.ok` means HTTP status is not 2xx (not success). It parses the error body
and throws it so the caller can catch it.

`hasBody()` checks for 204 No Content — if the backend returns no body, don't try
to parse JSON or it will crash.

---

## `apiRequest()` — authenticated requests

```ts
export async function apiRequest<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const authStore = useAuthStore()

  const headers = { 'Content-Type': 'application/json', ...options.headers }

  if (authStore.token) {
    headers['Authorization'] = `Bearer ${authStore.token}`
  }

  const response = await fetch(`${API_BASE}${endpoint}`, { ...options, headers })

  if (response.status === 401) {
    authStore.logout()
    if (onUnauthorized) onUnauthorized(message)
    throw error
  }

  if (response.status === 403) {
    if (onForbidden) onForbidden(message)
    throw error
  }
  ...
}
```

Same as `rawRequest` but before sending, reads `authStore.token` and adds it to headers:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

Every protected API call automatically carries the token — no component needs to think about it.

### 401 vs 403

| Status | Meaning | Action taken |
|--------|---------|-------------|
| 401 | Not authenticated — token missing, invalid, or expired | Log out + redirect to login |
| 403 | Authenticated but not authorized — wrong role | Show error, stay logged in |

A regular user hitting an admin endpoint gets 403.
An expired or missing token gets 401.

On 401, `authStore.logout()` is called automatically — the in-memory state is cleared
and the user is redirected to login. This happens globally for every endpoint — no component
needs to handle it.

On 403, the user is still authenticated, just not permitted for that specific action.
They are not logged out.

---

## `apiRequest` is a shared utility, not a composable

`apiRequest` is a plain async function. A Vue composable uses lifecycle hooks like
`onMounted`, `onUnmounted`, `ref`, `computed` etc. `apiRequest` uses none of those.

The naming convention makes this clear:
- `useAuthSync`, `useBuildStatus` — `use` prefix = Vue composable
- `apiRequest`, `rawRequest` — no `use` prefix = plain utility function

Composables sit one layer above `apiRequest` and use it indirectly through `endpoints.ts`.

---

## Standardization — why this matters

`apiRequest` is the standardization layer. Token injection, 401/403 handling, JSON parsing
all happen once in one place for every request in the entire app.

`endpoints.ts` wraps every backend endpoint in a typed function that calls `apiRequest()`:

```ts
getBuild:      (id: string) => apiRequest(`/api/builds/${id}`)
getProvenance: (id: string) => apiRequest(`/api/builds/${id}/provenance`)
submitFlag:    (id: string, flag: string) => apiRequest(`/api/builds/${id}/flag`, {
  method: 'POST',
  body: JSON.stringify({ flagValue: flag })
})
```

If you ever needed to change how auth headers work, you change it in `apiRequest`
and it applies everywhere automatically.
