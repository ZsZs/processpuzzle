import { ArtifactAttr, BaseEntity } from '@processpuzzle/base-entity';

export const ISSUED_DOCUMENT_ENTITY_NAME = 'IssuedDocument';

/**
 * A signed report, as the organization's *Issued Documents* list shows it: one `DocumentSignature` row of
 * base-document — the signature **is** the issued document, there is no second record — with the PDF that was
 * stored when it was signed. Read-only: only the server writes it.
 */
export class IssuedDocument implements BaseEntity {
  constructor(
    public id?: string,
    /** `<document title> – <object id>`, the row's link to its details. */
    public name = '',
    public documentId = '',
    public title = '',
    public locale = '',
    public revision = 0,
    public subjectType = '',
    public subjectId = '',
    public signedBy = '',
    public signedAt = '',
    /** Absent while the signer's upload has not (yet) succeeded. */
    public pdf?: ArtifactAttr,
  ) {}
}
