import type { Request } from 'express';
import type { StoredDocument } from './base-document.model.js';

/**
 * Authorization seam, mirroring `DocumentGuard`/`DocumentAccessPolicy` on the Java side.
 *
 * Denial maps to status the same way the Java implementation does, and the asymmetry is deliberate
 * there: **read denial answers 404, not 403**, so that probing ids cannot distinguish "does not
 * exist" from "exists but is not yours". Edit denial answers 403, because by then the caller has
 * already been shown the document exists.
 *
 * `principalOf` returns the identity to stamp into `createdBy`.
 */
export interface DocumentAccessPolicy {
  mayAccessOrganization(request: Request, orgKey: string): boolean;
  mayRead(request: Request, document: StoredDocument): boolean;
  mayEdit(request: Request, document: StoredDocument): boolean;
  principalOf(request: Request): string | null;
}

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
export class PermitAllDocumentAccessPolicy implements DocumentAccessPolicy {
  mayAccessOrganization(): boolean {
    return true;
  }

  mayRead(): boolean {
    return true;
  }

  mayEdit(): boolean {
    return true;
  }

  /**
   * Firebase Hosting forwards a verified `Authorization: Bearer <id-token>` untouched, so once the
   * token is verified this becomes the `sub`/`email` claim. Until then nothing is trustworthy enough
   * to record as an author.
   */
  principalOf(): string | null {
    return null;
  }
}
