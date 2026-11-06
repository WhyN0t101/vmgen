# Router — `src/router/index.ts`

## What a router is

In a traditional website, every URL change requests a new HTML page from the server.
In Vue (a Single Page Application), the browser downloads the app once. The router
intercepts URL changes and swaps which component is shown — no server round-trip for navigation.

`createRouter` is Vue Router's setup function.
`createWebHistory` makes URLs look normal (`/dashboard`) instead of hash-based (`/#/dashboard`).

---

## Route definitions

Each route is an object with three fields:

```ts
{
  path: '/dashboard',               // the URL
  name: 'dashboard',               // reusable internal name
  component: () => import('...'),  // which page to show
}
```

### Navigating by name

```ts
router.push({ name: 'login' })  // safer than router.push('/login')
```

Using `name` means if you ever change a path, you only update it in the router — not everywhere it's referenced.

### Lazy loading

The `() => import(...)` syntax means the page's JavaScript is only downloaded when someone
first visits that route, not when the app starts. All routes use this pattern.

---

## `meta` — access labels

```ts
{ path: '/generate', meta: { requiresAuth: true } }
{ path: '/admin',    meta: { requiresAuth: true, requiresAdmin: true } }
```

`meta` is just a plain object attached to a route. Vue Router does nothing with it on its own.
It is only meaningful because the navigation guard reads it.

---

## Navigation guard

```ts
router.beforeEach((to, _from, next) => {
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    next({ name: 'login', query: { redirect: to.fullPath } })
    return
  }

  if (to.meta.requiresAdmin && !authStore.isAdmin) {
    next({ name: 'dashboard' })
    return
  }

  next()
})
```

`beforeEach` fires before every navigation — link clicks, `router.push()`, typing a URL directly.

- `to` — route you're trying to go to
- `_from` — where you came from (unused, hence `_` prefix)
- `next` — function you must call to allow or redirect the navigation

### The `redirect` query param

```ts
next({ name: 'login', query: { redirect: to.fullPath } })
```

Passes the original URL so after login the user is sent back where they were trying to go.

### Admin redirect

A non-admin user hitting `/admin` is silently redirected to `/dashboard` — no error shown.

### Fallback

The `try/catch` around the guard redirects to login on any unexpected error, so a guard
crash never accidentally exposes a protected route.

---

## Key point — UX vs security

The router guard is UX-level protection only. It prevents a normal user from seeing a
broken admin page that can't load data. It is not a security boundary.

The real security is on the backend: every `/api/admin/**` endpoint has
`@PreAuthorize("hasRole('ADMIN')")`. Even if the router guard was completely removed,
a non-admin user's API calls would return 403 because their JWT contains `role: USER`.

Removing the admin guard → user sees the admin page render → every API call returns 403
→ page shows empty data or errors. The data never loads.
