# Composable — `useAuthSync.ts`

## What it does

If you have two tabs open and log out in one, this composable makes the other tab
log out automatically — without touching localStorage or making a server call.

---

## BroadcastChannel — what it is

A browser API that lets tabs on the same origin talk to each other directly through messages.
No server involved, no storage involved — pure in-browser tab-to-tab communication.

```ts
// sending
const channel = new BroadcastChannel('vmgen_auth')
channel.postMessage({ type: 'logout' })

// receiving (in another tab)
channel.addEventListener('message', (event) => {
  console.log(event.data) // { type: 'logout' }
})
```

Any tab that has a channel open with the same name receives the message.
The tab that sent it does not receive its own message.

### Analogy

BroadcastChannel is like a WebSocket but sideways:
- WebSocket = client talks to server (different machines)
- BroadcastChannel = tab talks to tab (same browser, same origin)

Both are persistent listeners that react to incoming messages.

---

## The code explained

```ts
export function useAuthSync() {
  const router = useRouter()
  const authStore = useAuthStore()
  const { show } = useToast()

  let channel: BroadcastChannel | null = null
```

Gets references to the router (for redirecting), the auth store (for logging out),
and the toast system (for showing a message). `channel` starts as null and is created on mount.

```ts
function handleMessage(event: MessageEvent) {
  if (event.data?.type === 'logout' && authStore.isAuthenticated) {
    authStore.logout()
    show('You have been logged out in another tab.', 'info')
    router.push('/login')
  }
}
```

Runs when a message arrives from another tab. Two conditions must both be true:
- The message type is `'logout'`
- This tab is currently authenticated (no point logging out if already logged out)

If both true: clear the auth store, show a toast, redirect to login.

```ts
onMounted(() => {
  try {
    channel = new BroadcastChannel(AUTH_CHANNEL)
    channel.addEventListener('message', handleMessage)
  } catch { /* BroadcastChannel unavailable in some environments */ }
})

onUnmounted(() => {
  channel?.close()
})
```

`onMounted` — runs when the component appears in the DOM. Creates the channel and starts listening.

`onUnmounted` — runs when the component is destroyed. Closes the channel to free the resource.
`?.` means "only call close() if channel is not null."

The `try/catch` handles older browsers where BroadcastChannel is not available.

### Why `onMounted` / `onUnmounted` make this a composable

A plain function runs once and returns. A composable hooks into the Vue component lifecycle —
it can set things up when a component mounts and clean up when it unmounts.

If `onUnmounted` was missing, the channel would stay open after navigation, the handler
would still fire, and it might try to redirect a component that no longer exists — a memory leak.

---

## Where the logout message comes from

In `auth.ts`, the `logout()` function broadcasts the message:

```ts
function logout() {
  // ... clear refs ...
  new BroadcastChannel(AUTH_CHANNEL).postMessage({ type: 'logout' })
}
```

So the full chain is:

```
Tab A: user clicks logout
    → authStore.logout() clears token in Tab A
    → BroadcastChannel.postMessage({ type: 'logout' })

Tab B: handleMessage fires
    → event.data.type === 'logout' ✓
    → authStore.isAuthenticated ✓ (Tab B still has its own token in memory)
    → authStore.logout() clears Tab B's token
    → toast: "You have been logged out in another tab."
    → router.push('/login')
```

Tab B's token was completely independent in memory — it had its own copy from when it logged in.
BroadcastChannel is the only way to coordinate between tabs without using storage.

---

## What about login — does it sync too?

No. `auth.ts` also broadcasts `{ type: 'login' }` but `useAuthSync` only listens for `'logout'`.

This is intentional. You would never want opening a new tab to automatically authenticate it
without user action. But logging out should propagate everywhere immediately — leaving an
authenticated tab open after logout is a security risk.
