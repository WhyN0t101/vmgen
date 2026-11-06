# Composable — `useSessionMonitor.ts`

## What it does

After login, the JWT has an expiry time. This composable watches that expiry and does two things:
- Warns the user 5 minutes before the session dies
- Automatically logs them out and redirects to login when it expires

Without it, the user would be mid-action — filling out a form, waiting for a build — and
suddenly get a 401 from the backend with no explanation. The session monitor makes expiry
predictable and visible.

---

## The code explained

```ts
export function useSessionMonitor() {
  const router = useRouter()
  const authStore = useAuthStore()
  const sessionStore = useSessionStore()
  const { show } = useToast()
```

Gets references to what it needs. `sessionStore` is a small Pinia store that holds the
two timer IDs so they can be cancelled later.

---

## `startMonitoring(expiresAt: string)`

```ts
function startMonitoring(expiresAt: string) {
  stopMonitoring()

  const expiryTime = new Date(expiresAt).getTime()
  const now = Date.now()
  const timeUntilExpiry = expiryTime - now
```

`expiresAt` comes from the backend as an ISO string like `"2026-05-25T14:30:00Z"`.
`.getTime()` converts it to milliseconds since epoch.
Subtracting `Date.now()` gives how many milliseconds until it expires.

`stopMonitoring()` at the start clears any existing timers — prevents two sets of timers
running if `startMonitoring` is called more than once.

---

## The warning timer

```ts
const warningTime = timeUntilExpiry - (5 * 60 * 1000)

if (warningTime > 0) {
  sessionStore.warningTimeoutId = window.setTimeout(() => {
    if (!sessionStore.warningShown && authStore.isAuthenticated) {
      show('Your session will expire in 5 minutes. Save your work.', 'warning')
      sessionStore.warningShown = true
    }
  }, warningTime)
}
```

`5 * 60 * 1000` is 5 minutes in milliseconds.
`warningTime` = how long to wait before showing the warning.

`window.setTimeout` fires once after that delay. Conditions inside:
- `!sessionStore.warningShown` — don't show the warning twice
- `authStore.isAuthenticated` — don't show it if the user already logged out manually

The timer ID is saved in `sessionStore.warningTimeoutId` so it can be cancelled if the
user logs out before the warning fires.

---

## The expiry timer

```ts
if (timeUntilExpiry > 0) {
  sessionStore.expiryTimeoutId = window.setTimeout(() => {
    if (authStore.isAuthenticated) {
      authStore.logout()
      show('Your session has expired. Please log in again.', 'info')
      router.push({
        path: '/login',
        query: { redirect: router.currentRoute.value.fullPath }
      })
    }
  }, timeUntilExpiry)
} else if (authStore.isAuthenticated) {
  // token already expired before this ran
  authStore.logout()
  show('Your session has expired. Please log in again.', 'info')
  router.push('/login')
}
```

Fires at the actual expiry time. When it fires:
1. Calls `authStore.logout()` — clears the in-memory token
2. Shows a toast so the user knows why they were redirected
3. Pushes to `/login` with the current path as a redirect query param — so after re-login
   the user lands back where they were

The `else if` handles the edge case where the token was already expired before
`startMonitoring` was called — for example if the page was left open in the background
for hours and only checked later.

---

## `stopMonitoring()`

```ts
function stopMonitoring() {
  sessionStore.clearTimeouts()
}
```

Cancels both timers. Called:
- At the start of `startMonitoring` — to reset before setting new timers
- When the user manually logs out — no point firing an expiry timer for a gone session

---

## Full timeline

```
Login
  → backend returns expiresAt: "2026-05-25T15:00:00Z"
  → startMonitoring("2026-05-25T15:00:00Z") called
  → timeUntilExpiry = 60 minutes in ms
  → warning timer set for 55 minutes from now
  → expiry timer set for 60 minutes from now

55 minutes later
  → toast: "Your session will expire in 5 minutes. Save your work."

60 minutes later
  → authStore.logout() — token cleared from memory
  → toast: "Your session has expired. Please log in again."
  → router.push('/login?redirect=/current-page')
```

---

## Key point — client-side convenience only

This is entirely client-side. The backend does not know about these timers.
The JWT expiry is baked into the token itself — the backend validates it on every request
regardless of what the frontend does.

If the user somehow disabled JavaScript timers, they would just get a 401 from the next
API call instead of a friendly warning message.

The session monitor exists to make a predictable, user-friendly experience around something
the backend enforces anyway.
