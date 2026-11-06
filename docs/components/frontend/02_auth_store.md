# Auth Store — `src/stores/auth.ts`

## What a store is

Components in Vue are isolated — they don't naturally share data with each other.
A store is a place to keep global state that any component can read or write.
Pinia is the library used for this.

```ts
defineStore('auth', () => { ... })
```

Creates a store named `'auth'`. The function inside works like a Vue component's `setup()` —
you declare reactive variables and return what you want to expose to the rest of the app.

---

## Reactive variables

```ts
const user      = ref<User | null>(null)
const token     = ref<string | null>(null)
const role      = ref<'user' | 'admin' | null>(null)
const expiresAt = ref<string | null>(null)
```

`ref()` makes a value reactive — whenever it changes, anything in the app that reads it
updates automatically.

`<User | null>` is TypeScript saying "this can be a User object or null" — type safety only,
not logic. The `(null)` is the actual starting value. All four start as `null` because nobody
is logged in yet.

---

## Computed values

```ts
const isAuthenticated = computed(() => !!token.value)
const isAdmin = computed(() => (user.value?.role || role.value)?.toLowerCase() === 'admin')
```

`computed()` is a value that derives itself from other reactive values. You don't set it
manually — it recalculates automatically when its dependencies change.

### When to use `computed` vs a regular function

- A function recalculates every time you call it
- A `computed` caches the result and only recalculates when its dependencies actually changed

So `isAuthenticated` doesn't rerun unless `token.value` changes. More efficient, and it
integrates with Vue's reactivity so the UI updates automatically.

### `isAuthenticated`

`!!token.value` converts the token string to a boolean:
- `null` → `false` (not authenticated)
- `"eyJ..."` → `true` (authenticated)

"Is authenticated" simply means "do we have a token in memory."

### `isAdmin`

Checks if role is `'admin'`. The `?.` is optional chaining — if `user.value` is null,
it doesn't crash, it returns `undefined` which becomes `false`.

This is what the router guard reads to allow or block `/admin` routes.

---

## The `login()` function

```ts
async function login(identifier: string, password: string) {
  const data = await rawRequest<{...}>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ identifier, password })
  })

  token.value = data.token
  role.value = roleValue
  expiresAt.value = data.expiresAt
  user.value = { id: data.userId, email: identifier, ... }

  new BroadcastChannel(AUTH_CHANNEL).postMessage({ type: 'login' })
}
```

Uses `rawRequest` (not `apiRequest`) because at login time you don't have a token yet —
there is nothing to inject into the Authorization header.

The backend returns `{ token, role, expiresAt, userId }`. These are stored into the four refs.
The moment `token.value` changes from `null` to a string, `isAuthenticated` automatically
becomes `true`, the router guard updates, everything reacts.

The BroadcastChannel message notifies other open tabs that a login happened. Other tabs
do not automatically log in — they just receive the notification (which is currently ignored).

---

## The `logout()` function

```ts
function logout() {
  const t = token.value
  rawRequest<void>('/api/auth/logout', { method: 'POST', ... }).catch(() => {})

  user.value = null
  token.value = null
  role.value = null
  expiresAt.value = null

  new BroadcastChannel(AUTH_CHANNEL).postMessage({ type: 'logout' })
}
```

Two things happen:

1. Best-effort server call to invalidate the session server-side. `.catch(() => {})` means
   if it fails (network down, token already expired), it doesn't matter — local logout still happens.

2. All four refs are set back to `null`. The moment `token.value = null`, `isAuthenticated`
   becomes `false`, and the router guard blocks protected routes again.

"Best-effort" is intentional — the JWT is also invalidated server-side on expiry, so even
if the logout API call failed, the token would eventually expire and the backend would reject it.

The BroadcastChannel message tells other tabs to also log out. `useAuthSync` (see that file's
notes) listens for this and handles it.

---

## The security design

The JWT lives only in these four `ref()` variables — plain JavaScript memory.
When the page refreshes, JavaScript resets, the refs go back to `null`, and the user
must log in again.

Nothing is written to `localStorage`, `sessionStorage`, or cookies.

An XSS attack that runs `localStorage.getItem('token')` or `document.cookie` gets nothing.
The only way to get the token is to be running JavaScript in the page at the moment the
user is logged in — which is a much harder attack than reading persistent storage.
