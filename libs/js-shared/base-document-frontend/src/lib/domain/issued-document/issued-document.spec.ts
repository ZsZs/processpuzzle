import { FormControlType } from '@processpuzzle/base-entity';
import { describe, expect, it } from 'vitest';
import { ISSUED_DOCUMENT_ENTITY_NAME } from './issued-document';
import { createIssuedDocumentDescriptor } from './issued-document.descriptors';
import { IssuedDocumentMapper } from './issued-document.mapper';

describe('IssuedDocument', () => {
  it('maps a signature row, naming it after the document and the object', () => {
    const issued = new IssuedDocumentMapper().fromDto({
      id: 's-1',
      documentId: 'd-1',
      title: 'Order Invoice',
      locale: 'en',
      revision: 2,
      subjectType: 'Order',
      subjectId: 'o-1',
      signedBy: 'Ada',
      signedAt: '2026-10-19T09:00:00Z',
      pdf: { bucket: 'b', objectId: 'x', name: 'f.pdf', mimeType: 'application/pdf' },
    });

    expect(issued.name).toBe('Order Invoice – o-1');
    expect(issued.pdf?.objectId).toBe('x');
  });

  it('leaves the PDF out while the upload is pending', () => {
    expect(new IssuedDocumentMapper().fromDto({ id: 's-1', pdf: null }).pdf).toBeUndefined();
  });

  it('is a read-only descriptor with an ARTIFACT column for the PDF', () => {
    const descriptor = createIssuedDocumentDescriptor();
    const attrs = JSON.stringify(descriptor.attrDescriptors);

    expect(descriptor.entityName).toBe(ISSUED_DOCUMENT_ENTITY_NAME);
    expect(descriptor.isReadOnly).toBe(true);
    expect(attrs).toContain(`"formControlType":"${FormControlType.ARTIFACT}"`);
  });
});
