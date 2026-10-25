# Content Book

*2026-10-18*

A **content book** groups existing documents into a navigable tree with its own access rights — a
Confluence space rather than a folder. It lives in `base-document`, frontend and backend; the
document-agnostic building blocks it renders with (`pp-tree-nav`, the sidenav slot) live in
`@processpuzzle/widgets`.

## Model

| Field | Meaning |
| --- | --- |
| `slug` | Unique per organization. The address the widget and the seed file use, since ids are generated. |
| `title`, `description` | Shown in the sidenav header and the CRUD list. |
| `isPublic`, `readerRoles`, `editorRoles` | Who may see the tree and who may change it — the document access fields. |
| `nodes` | `[{id, documentId, parentId, orderIndex}]`, stored on the book as one JSON column. |

- **The book owns the tree; the documents are untouched.** A document may appear in several books, and a
  reorder is a single `PUT …/content-books/{id}/nodes` that replaces the whole tree.
- `ContentBookNodes` validates on construction: every parent is in the book, no cycles, a document at most
  once, sibling `orderIndex` normalized to `0..n-1`. Only `kind = DOCUMENT` may be placed — report
  templates are not pages.
- **Deleting a document** detaches it from every book of the organization; its children move up into its
  place. The tree's own "Remove from book" keeps the document.

## Access

The book reuses `DocumentGuard`'s ladder through overloads that take `(orgKey, isPublic, roles)` instead of a
`Document` — one ladder, so the two cannot drift. As for documents, a reader who may not see a book gets
**404**; an editor check that fails is **403**.

The book's rights govern the *tree*, not the pages: each page is still read through the document's own
rules. A public book may therefore list a page its reader is told is not published (the seeded handbook does
this with the release notes on purpose).

On the client, `DOCUMENT_ACCESS_CONTEXT` (optional, `{ roles: Signal<string[]> }`) only decides whether
mutations are *offered*. Without it the widget assumes an editor and lets the backend's 403 decide.

## The `content-book` widget

`pp-content-book` (`bookSlug`, `locale?`, `sidenav = true`, `startDocumentSlug?`; output `documentSelected`)
loads the book by slug, then its documents in one call, and renders the tree with `pp-tree-nav`. The selected
page is kept in the `?doc=<slug>` query parameter, so links are shareable.

Clicking a node shows the published reader. Without a published translation the view falls back to the draft
(with a banner) for editors, and to "not published yet" for everyone else.

The page provides a `DocumentDataContext` bound to its input ports' defaults, as the Preview tab does with the
values a host picks. A book hands a page no values, so an `ENTITY_COLLECTION` port lists what its
`defaultRsqlFilter` selects and an `ENTITY_REF` port without a `defaultValue` stays empty.

### Toolbar

A toolbar runs over the tree and the page. Inline it carries the book's title; in the sidenav the title is in the
sidenav header, and readers get no toolbar there at all. Editors of the book get, acting on the selected page:

| Control | Does |
| --- | --- |
| Status chip | `Draft · r2`, `Published · r3`, `Published r3 · draft r4` or `Archived` — the page's translation in the reader's language, else the source language. |
| *Unpublished* toggle | On by default. Off folds away pages that have nothing published, and archived ones; their children move up into their place. Readers always see the folded tree. |
| Add ▾ | Above the selected page, below it, as its child, or at the top level. Opens the same dialog as before. |
| Edit / Read | Swaps in the block editor. Editing a published page edits its draft (`PUBLISHED_WITH_DRAFT_CHANGES`); readers keep the published revision until the next Publish. Disabled on an archived page. |
| Publish | `POST …/translations/{locale}/publish` for the shown translation; disabled when it is published already. Needs a publisher role (`publisherRoles`, else `editorRoles`). |
| Archive | After confirming, `POST …/documents/{id}/archive`. Publisher role. |
| Properties | The page's Documents details form, through `BaseFormNavigatorSingletonStore.navigateToUrl`, so Cancel and Save come back to the book. The URL is the nearest ancestor route that mounts `BASE_DOCUMENT_ROUTES`; where there is none the button is disabled. |

The node menu keeps the arranging verbs.

## Archive

`ArchiveDocument` writes a JSON snapshot — properties, and per locale the draft and the published blocks with
their revisions — through the `DocumentFileStore` port that signed reports use, as `<slug>-archive.json`. Then
it records `archive {archivedAt, archivedBy, bucket, objectId, name, mimeType}` on the document. With no store
wired the call is a 503 and nothing changes. A second archive is a 409.

An archived document is read-only: every use case that writes to it, except Delete, calls
`DocumentGuard.requireWritable` and answers 409 `document.archived`. Published content stays readable. There is
no unarchive yet.

## Sidenav takeover

`SidenavContentService` (root) holds one portal, a title and a `content | menu` mode.
`pp-sidenav-content-outlet` wraps a shell's own navigation: while a portal is attached and the mode is
`content` it renders the portal under a header with a toggle back to the menu; otherwise it projects the
shell's nav unchanged. Both `AppShellComponent` and the testbed shell wrap their sidenav in it.

The widget attaches its tree when `sidenav` is true, an outlet exists and the device is not small; otherwise it
renders the tree inline in a two-column layout. `attach` returns a detach function, so the menu comes back when
the widget is destroyed.

## Seeding

`SampleContentBookLoader` reads `classpath*:sample-content-books/*-content-books.yaml` after the sample
documents, organization taken from the file name as for every other loader. Nodes name their document by
`documentSlug`, resolved to an id at import. Create-only: a book that exists is never touched, so a tree an
editor rearranged survives a restart. The testbed seeds `testbed-handbook`, shown at
the Content tab of the book at `/base-document/samples/content-book/list`.

## Not done

- `pp-tree-nav` is exported but not a registered widget.
- No drag and drop — the tree is rearranged through each node's action menu.
- No unarchive, and no screen that lists or downloads archive snapshots.
