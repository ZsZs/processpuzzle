"use strict";
/**
 * Configuration of the `baseDocument` function. Values are read at module load, which is what
 * lets a spec swap them with `vi.mock('./base-document.config.js', ...)`.
 */
var _a;
Object.defineProperty(exports, "__esModule", { value: true });
exports.DEFAULT_PAGE_SIZE = exports.MAX_LIST_SCAN = exports.PUBLISHED_COLLECTION = exports.DRAFTS_COLLECTION = exports.DOCUMENTS_COLLECTION = exports.ORGANIZATIONS_COLLECTION = exports.API_BASE_PATH = void 0;
/**
 * Path the Hosting rewrite delivers to this function; see the `/api/organizations` rewrites in
 * `firebase.json`. Unlike `objectStore`'s `/api/store`, the prefix cannot be more
 * specific than `/api`, because the contract owns everything after it: the operation paths are
 * `/organizations/{orgKey}/documents...`, so `/api` is all that is left to strip.
 */
exports.API_BASE_PATH = '/api';
/** Firestore collection ids. The document collection is nested under `organizations/{orgKey}`. */
exports.ORGANIZATIONS_COLLECTION = 'organizations';
exports.DOCUMENTS_COLLECTION = 'documents';
exports.DRAFTS_COLLECTION = 'drafts';
exports.PUBLISHED_COLLECTION = 'published';
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
exports.MAX_LIST_SCAN = Number((_a = process.env.BASE_DOCUMENT_MAX_LIST_SCAN) !== null && _a !== void 0 ? _a : 1000);
/** Contract default of the `size` query parameter (`SizeParam`). */
exports.DEFAULT_PAGE_SIZE = 20;
//# sourceMappingURL=base-document.config.js.map