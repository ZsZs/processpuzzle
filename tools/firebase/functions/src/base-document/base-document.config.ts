/**
 * Configuration of the `baseDocument` function. Values are read at module load, which is what
 * lets a spec swap them with `vi.mock('./base-document.config.js', ...)`.
 */

/**
 * Path the Hosting rewrite delivers to this function; see the `/api/organizations` rewrites in
 * `firebase.json`. Unlike `objectStore`'s `/api/store`, the prefix cannot be more
 * specific than `/api`, because the contract owns everything after it: the operation paths are
 * `/organizations/{orgKey}/documents...`, so `/api` is all that is left to strip.
 */
export const API_BASE_PATH = '/api';

/** Firestore collection ids. The document collection is nested under `organizations/{orgKey}`. */
export const ORGANIZATIONS_COLLECTION = 'organizations';
export const DOCUMENTS_COLLECTION = 'documents';
export const DRAFTS_COLLECTION = 'drafts';
export const PUBLISHED_COLLECTION = 'published';

/**
 * Upper bound on the documents `listDocuments` reads before filtering and paging in memory.
 *
 * Firestore cannot evaluate RSQL — no substring match, no case-insensitive compare, and an
 * equality filter combined with an unrelated `orderBy` needs a hand-maintained composite index
 * per field pair. Reading the organization's documents and filtering here keeps
 * `firestore.indexes.json` empty and makes `totalElements` exact, at the cost of reads that grow
 * with the organization's document count. A CMS organization holds tens to hundreds of documents,
 * so the trade is deliberate; crossing this cap is logged rather than silently truncated.
 */
export const MAX_LIST_SCAN = Number(process.env.BASE_DOCUMENT_MAX_LIST_SCAN ?? 1000);

/** Contract default of the `size` query parameter (`SizeParam`). */
export const DEFAULT_PAGE_SIZE = 20;
