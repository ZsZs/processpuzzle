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

Clicking a node shows the published reader; editors get an **Edit** toggle that swaps in the block editor.
Without a published translation the view falls back to the draft (with a banner) for editors, and to "not
published yet" for everyone else. Publishing stays on the Documents screen.

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
`/base-document/samples/handbook`.

## Not done

- `pp-tree-nav` is exported but not a registered widget.
- No drag and drop — the tree is rearranged through each node's action menu.
