# @processpuzzle/platform-admin

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-platform-admin-frontend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_platform_admin_frontend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_platform_admin_frontend)
[![Node version](https://img.shields.io/npm/v/%40processpuzzle%2Fplatform-admin?style=flat)](https://www.npmjs.com/package/@processpuzzle/platform-admin)

## Introduction

`@processpuzzle/platform-admin` is the front end ProcessPuzzle **staff** use to administer tenants. It is
not part of any customer's application: it drives the `/platform/**` surface of
[`platform-admin-backend`](../../java-shared/platform-admin-backend/README.md), which is the module
that owns the `Organization` aggregate and the Keycloak realm behind each one.

What it lets an operator do:

- **List and search organizations** — paged, RSQL-filterable, showing the tenant's lifecycle status.
- **Provision, edit and delete** an organization.
- **Suspend / activate** a tenant. Suspending revokes access and disables the tenant's realm while
  retaining its data; activating reverses both.
- **Assign the first administrator** of a new organization — creates the user in the tenant's realm
  and grants it `org-admin`.
- **Read the tenant's billing** — plan, subscription, usage and invoices. Read-only by design: there
  is no payment provider in this platform, so nothing here can charge anyone.

The screens are the ordinary descriptor-driven [`@processpuzzle/base-entity`](../base-entity-frontend/README.md)
CRUD stack — `createOrganizationDescriptor()` drives the table, the form, search and PDF export, and
the suspend/activate/assign-admin verbs arrive as extra form actions rather than as bespoke screens.

## Host application

The library is mounted by [`apps/processpuzzle-admin-frontend`](../../../apps/processpuzzle-admin-frontend), which authenticates
against the fixed **platform** realm (not a tenant's). `PLATFORM_ADMIN_ROUTES` is the entry point:

```ts
{ path: 'platform', loadChildren: () => import('@processpuzzle/platform-admin').then((m) => m.PLATFORM_ADMIN_ROUTES) }
```

Every call it makes requires the `platform-admin` authority; a tenant's token gets 403.

## Development

```powershell
npm exec nx build platform-admin-frontend
npm exec nx test platform-admin-frontend
npm exec nx lint platform-admin-frontend
```

## License

MIT
