# @processpuzzle/org-admin

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-org-admin-frontend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_org_admin_frontend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_org_admin_frontend)
[![Node version](https://img.shields.io/npm/v/%40processpuzzle%2Forg-admin?style=flat)](https://www.npmjs.com/package/@processpuzzle/org-admin)

## Introduction

`@processpuzzle/org-admin` is the front end a **paying customer's own administrator** uses to manage
their organization: who may sign in, and what each of them may do. It is the counterpart of
[`@processpuzzle/platform-admin`](../platform-admin-frontend/README.md), which administers tenants
*from the outside*; this one administers one tenant *from the inside*, and can never see another.

What it lets a tenant administrator do:

- **List and search the organization's users** — paged, filterable.
- **Invite** a user, **enable / disable** one, **delete** one.
- **Assign and unassign realm roles** on a user, from the roles the tenant's realm declares.

There is no `users` table anywhere in ProcessPuzzle: **Keycloak is the system of record**, and
[`org-admin-backend`](../../java-shared/org-admin-backend/README.md) proxies its Admin API. A user
edited here appears in that tenant's realm, and the roles granted here land in the user's token on
its next login.

## Host application

Mounted inside `processpuzzle-biz-frontend` under the tenant's own URL space:

```ts
{ path: 'admin', loadChildren: () => import('@processpuzzle/org-admin').then((m) => m.ORG_ADMIN_ROUTES) }
```

so the reachable path is `/{orgKey}/admin/users`. `admin` is in `ReservedOrganizationKeys`, so no
tenant can ever claim that segment out from under the feature. The `orgKey` in the URL is *not* the
authorization decision — the backend compares the token's realm against it and answers 403 on
mismatch.

## Development

```powershell
npm exec nx build org-admin-frontend
npm exec nx test org-admin-frontend
npm exec nx lint org-admin-frontend
```

## License

MIT
