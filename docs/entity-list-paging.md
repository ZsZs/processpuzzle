# Entity lists: paging

## The problem

An entity list (`base-entity-list`) showed only the first 20 rows, and its pager said "11 – 20 of 20". The data was
complete: the common of Sail Race Control held 34 boat classes.

Two facts produced it:

1. The REST endpoint `GET /api/organizations/{org}/entities/{code}` answers one page, and without a `size` it answers
   20 rows (`EntityObjectEndpoint`: `size != null ? size : 20`). The list did not ask for a size.
2. The list counted the rows it had loaded, not the total of the query. The store does hold the server's
   `totalElements`, but the paginator of the list was fed by the loaded rows.

## What changed (option 1: read every page)

- `base-entity-list.component.ts` asks the store for every page: `allPages: true`. The store already supports it
  (`findByQuery.ts`, `readAllPages`): it reads the first page, then the rest one after another, `200` rows per request,
  up to `100` pages (20,000 rows), and answers them as one list. The pager then counts every row.
- A spec asserts the list asks for all pages.
- No backend change: the endpoint is unchanged.

This is right for the lists of the platform today. It is not a paging design: every row of a list is read into the
browser, so a list of tens of thousands of records would be slow, and beyond 20,000 rows the list is cut off.

## What has to be implemented in core for server-side paging (option 2)

Needed before a list can hold more rows than a browser should read:

1. **Server paging is the list's own:** the list asks for the page it shows (`page`, `size`), and the pager's length
   is the server's `totalElements`, not the loaded rows. The store already maps `totalElements` and `totalPages`
   (`base-entity-rest.service.ts`).
2. **Pager events go to the store:** page changes, and page size changes (the pager's `pageSizeOptions`), call
   `store.load` with the new `page` and `pageSize`. Today the list only loads once.
3. **Sorting and filtering on the server:** a sort of a column, and the filter/RSQL query, are sent to the endpoint.
   The list's sort is client-side today, which would only sort the loaded page.
4. **A default that is stated:** the endpoint's default page size (20) should be a documented constant, and the
   frontend should send an explicit `size` on every list request.
5. **Retired rows and the count:** `totalElements` must count the same rows the page shows (retired rows are counted
   only when shown, as `includeRetired` decides).
6. **Selection across pages:** the list's "select one row" and the details it opens stay valid when the page changes.
7. **Tests:** a spec per list behaviour (page change, size change, sort, filter, retired), and one end-to-end check
   against a list of more than one page.

Until these exist, `allPages` is the way a list shows all of its rows, and it is limited to 20,000 rows.
