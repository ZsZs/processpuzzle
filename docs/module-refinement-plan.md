# Module Refinement Plan

Status: **in progress** — phases 1–3 implemented, 4–6 open · Drafted 2026-10-26 · Owner: ZsZs

Refines `base-app`'s `ModuleDefinition` (introduced by the
[AppDefinition Refinement Plan](app-definition-refinement-plan.md), decision 2) so that a module carries
its own **translations** and **assets**. Routing decomposition and lazy loading already exist; this plan
adds the two missing parts and closes the gaps they expose.

## What a Module is for

| Purpose | Status |
| --- | --- |
| Break the routing tree into manageable chunks of child routes | **Done.** A module owns a flat route list; `ModuleMount { moduleKey, basePath }` mounts it; `buildAppRoutes` derives the nesting (`app-route-builder.ts`). |
| Mimic Angular's lazy-loaded libraries — build only the modules needed | **Done, for metadata.** `lazyMount` emits a `loadChildren` that fetches the module on first navigation into its mount. Widget *code* stays in the application bundle — a run-time-authored module has no bundle to split. The live app reads released modules from `/layout`; the Preview reads drafts. |
| Carry the module's i18n translations, loaded with the module | **New** — see [Translations](#translations). |
| Carry the module's assets (`.md`, images, …), available with the module | **New** — see [Assets](#assets). |

## Decisions

| # | Question | Decision |
| --- | --- | --- |
| 1 | Are translations edited in the app? | **No.** The user downloads prepared files, translates them off-line and uploads them again. The app shows coverage, never an editor. |
| 2 | What is in a prepared file? | Every translatable item of the module — module name and route titles — keyed and pre-filled. |
| 3 | Elements without a `translocoId` | Get a **derived default key**, the way `translocoScope` defaults to the module key. The file builder and the route renderer apply the same rule, so designers never have to type keys. |
| 4 | Which elements count as "the module's"? | Module name and route titles now. Widget property texts later, through a *translatable* marker on widget props schemas. Entity labels stay with base-entity's own translations. |
| 5 | Supported languages | The deployment's **`AVAILABLE_LANGUAGES`**, reached by base-app through a new outbound port (`SupportedLanguages`). That list exists only in the frontend's run-time configuration, so the backend carries its own copy, `base-app.supported-languages`, which has to be kept in step by hand. A per-organization setting may replace both later. |
| 6 | Packaging | **Translations as one zip** (one `<lang>.json` per language, translated side by side). **Assets uncompressed**, file by file. |
| 7 | Translation storage | **Backend rows** (jsonb, per module / channel / language), not ARTIFACT files in MinIO: the server validates them, the loader reads them directly, and a release copies them like the module itself. |
| 8 | Asset storage | **MinIO, content-addressed**, through a base-app outbound port. The module records the asset list; a release copies the list, never the files. |

## Gaps this plan closes

- **Translations cannot be written.** `/app/translations/{scope}/{locale}` is read-only; bundles arrive
  only by startup seeding (`AppTranslationLoader`).
- **Translations and files have no channel.** Modules have `DRAFT` / `RELEASED` rows, but
  `AppTranslationBundle` does not, and no `ReleaseParticipant` touches stored files. Without a channel an
  uploaded translation or asset would be live immediately, bypassing the release.
- **Supported languages are not known to the backend.** They exist only in the frontend's run-time
  configuration (`run-time-conf/config.common.json`). base-app may depend on `core` and `shared` only,
  so it needs a port.
- **The object store cannot hold module assets as they are.** It keys objects by random UUID, routes
  them to buckets by mime type, has no listing and no per-organization prefix.

## Translations

### Keys

A module's keys live in its transloco scope (`moduleTranslocoScope`: `translocoScope`, else the key).
Inside the files they are **relative to the scope**, as in `assets/i18n/<scope>/<lang>.json`.

| Element | Key | Default-language text |
| --- | --- | --- |
| Module name | `translocoId`, else `<scope>.module.name` | `name` |
| Route title | `translocoId`, else `<scope>.route.<path>` (path verbatim, e.g. `route.orders/:id`) | `title` |

A `translocoId` outside the module's scope (e.g. `base_app.…`) is not the module's to translate: it is
left out of the files and reported as a warning. The derived rule matches the existing seeded style —
`order_admin.route.orders` is what route `orders` of module `order-admin` would derive anyway.

Files are written **flat** (`"route.orders/:id": "…"`), ordered as the elements are; uploads accept flat
or nested JSON and are flattened. Transloco reads both.

### Download — the prepared files

`GET /organizations/{orgKey}/modules/{moduleKey}/translations` → `application/zip`, built on the fly with
`ZipOutputStream`, one `<lang>.json` per supported language (the backend knows the list, so the call takes no
parameter). The organization's default language always comes first, even when the list leaves it out.

For each key, the value is the **uploaded translation if there is one, else the authored text**. So the
default language comes out complete, and every other language shows the translator what to translate.
Keys the module no longer uses are not written.

### Upload

`PUT /organizations/{orgKey}/modules/{moduleKey}/translations` (multipart `file`: the zip, or a single
`<lang>.json`). Always writes the **draft**; one bundle per language is replaced.

- **Rejected:** malformed zip or JSON, non-string values, a language that is not supported, entries
  outside the archive root. Size and entry caps follow the starter-bundle reader (`BundleReader`).
- **Reported as warnings** in the response (`ModuleTranslationUploadReport`, per language): unknown keys
  (dropped), missing keys, values identical to the authored text (probably untranslated; never for the
  default language). A `translocoId` outside the module's scope is simply left out of the files.

### Coverage

`GET /organizations/{orgKey}/modules/{moduleKey}/translation-coverage?locale=…` — per language: key
count, translated count, missing keys. Translated means stored, and for a non-default language different
from the authored text.

### Storage and release

- New entity `ModuleTranslation`, table `app_module_translations`, key
  `(org_key, module_key, channel, locale)`, `messages` jsonb. Keyed by **module**, not scope, so renaming
  the scope keeps the translations. Separate from `AppTranslationBundle`, which holds the framework's
  seeded bundles rather than tenant content.
- `ModuleReleaseParticipant` copies the drafts onto the released rows and deletes released rows whose draft
  is gone; its fingerprint includes the translations, so an upload shows as a pending change.
- `DeleteModuleDefinition` deletes the draft translations; `TenantDataCleaner` erases a tenant's rows.

### Loading at run time

`GET /organizations/{orgKey}/modules/{moduleKey}/translations/{locale}?revision=` → the stored bundle,
scope-relative. `lazyMount` registers the module's scope with a transloco **inline loader** per language
(`ModuleTranslationLoader`, built by `AppShellRoutesFactory`) — draft for a draft definition (the Preview),
released for a published layout — so the global `TranslocoHttpLoader` stays as it is. The inline loader lays
the uploaded bundle **over** what the scope already ships (asset, or the seeded `app_translations` bundle), so a
module translated the old way, such as the seeded `order_admin`, keeps its words. `lazyMount` also gives every
route the derived `translocoId` (`moduleRouteTranslocoId`) before rendering.

Known limitation: transloco caches a scope per language, so the Preview shows an upload only after a reload.

## Assets

### Authoring

The Module form gets an **Assets** section: a list of files with path, type and size; upload one or more
files into an optional folder; download, rename (path) and delete. No archives. Paths are relative to the
module (`docs/welcome.md`, `docs/img/flow.png`) so markdown can link its images relatively; they follow
the safe-path rule of `BundleReader`.

### Storage

- Outbound port `ModuleAssetStore` in `app :: port`, adapter in the testbed backend over
  `FileStorageService` — the pattern base-document already uses (`DocumentPortsConfiguration`).
- One bucket `<prefix>-modules`, objects named by content: `<orgKey>/<sha256>`.
- The module records `assets: ModuleAsset[]` — `{ path, sha256, mimeType, size }`. Because the list is
  part of the module row, **a release is a database copy** with no file copying, transactional like every
  other participant. Unreferenced objects are swept later; a tenant erase deletes its prefix.

### Serving

`GET /organizations/{orgKey}/modules/{moduleKey}/assets/{**path}?revision=` streams the file through the
backend, with `ETag` and long-lived caching (content-addressed, so a path's bytes change only with its
hash).

- Streamed rather than redirected to a presigned MinIO URL: no CORS on MinIO, relative links keep
  working, one place checks organization access.
- SVG and HTML are served with a sandboxing `Content-Security-Policy` (and `nosniff`), since a script in
  an uploaded file would otherwise run on our origin.

### Referencing assets from widgets

- `lazyMount` provides a **module context** `{ moduleKey, channel, assetRoot }` to everything below the
  mount.
- Widget URL props — `markdownSrc`, the image widgets' `src` — are resolved through it: `module:<path>` or
  a relative path resolves into the module; an absolute URL is left alone.
- ngx-markdown's image and link renderer is overridden so relative links *inside* a `.md` resolve into the
  module too.
- `<img>` cannot send the bearer token, so images are fetched through `HttpClient` and shown as blob URLs,
  by a small pipe. Markdown is already fetched through `HttpClient`.
- "Available when the module is loaded" means the **asset list** arrives with the module and the files
  are fetched on first use. Preloading every file would undo the lazy loading.

## Phases

Status: **1–3 done** (backend: contract, key extraction, zip, validation, storage with channel, release, delete,
tenant erase; frontend: Translations section of the Module form, derived keys, inline loader). 4–6 open.

Each phase is one PR. Phases 2 and 4 add endpoints and beans, so the testbed backend's `contextLoads` and
`ModularityTests` run locally before the PR.

1. **Contract.** `base-app-api.yaml`: translation download / upload / coverage / bundle, asset endpoints,
   `ModuleAsset`; the supported-languages port.
2. **Translations backend.** Key extraction with derived defaults, zip build and parse, validation and
   report, `ModuleTranslation` with channel, release, delete and tenant erase.
3. **Translations frontend.** Module form panel (coverage table, Download, Upload), derived keys in the
   route renderer, inline loader in `lazyMount`, Preview cache reload.
4. **Assets backend.** `ModuleAssetStore` port and adapter, upload / delete / serve endpoints, release,
   sweep, tenant erase.
5. **Assets frontend.** Assets section of the Module form, module context, URL resolution in widgets,
   markdown renderer override, authenticated image pipe.
6. **Export / import and starter bundles** carry translations and assets; e2e; docs (base-app README,
   Feature maturity table).

## Out of scope

- Translating widget property texts (decision 4) — needs the *translatable* marker first.
- Code splitting of widget components per module.
- A per-organization language setting (decision 5).
