"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.PermitAllDocumentAccessPolicy = void 0;
/**
 * Permits everything, exactly as `PermitAllDocumentAccessPolicy` does out of the box.
 *
 * TODO(base-document): verify the Firebase Auth ID token and check its organization claim against
 * the `orgKey` path segment. Deliberately not done in this pass: the whole Firebase deployment is
 * currently unauthenticated — `jsonServer` serves rules and app-definitions with no token, and
 * `firestore.rules` allows all reads and writes until 2027 — so enforcing on documents alone would
 * add process, not protection. Tightening belongs in one change that covers every function, and this
 * interface is the seam it plugs into: construct `BaseDocumentHandlers` with a different policy and
 * nothing else moves.
 */
class PermitAllDocumentAccessPolicy {
    mayAccessOrganization() {
        return true;
    }
    mayRead() {
        return true;
    }
    mayEdit() {
        return true;
    }
    /**
     * Firebase Hosting forwards a verified `Authorization: Bearer <id-token>` untouched, so once the
     * token is verified this becomes the `sub`/`email` claim. Until then nothing is trustworthy enough
     * to record as an author.
     */
    principalOf() {
        return null;
    }
}
exports.PermitAllDocumentAccessPolicy = PermitAllDocumentAccessPolicy;
//# sourceMappingURL=document-access-policy.js.map