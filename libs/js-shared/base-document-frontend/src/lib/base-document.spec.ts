import { describe, expect, it } from 'vitest';
import { BaseDocument, DEFAULT_DOCUMENT_CONTENT_TYPE } from './base-document';

describe('BaseDocument', () => {
  it('exposes the name passed to the constructor', () => {
    const document = new BaseDocument('invoice.pdf');

    expect(document.name).toBe('invoice.pdf');
  });

  it('defaults the content type to the binary media type when not provided', () => {
    const document = new BaseDocument('invoice.pdf');

    expect(document.contentType).toBe(DEFAULT_DOCUMENT_CONTENT_TYPE);
    expect(document.isBinary).toBe(true);
  });

  it('reports a declared content type as not binary', () => {
    const document = new BaseDocument('invoice.pdf', 'application/pdf');

    expect(document.isBinary).toBe(false);
  });

  it('appends the content type to the description when one is declared', () => {
    const document = new BaseDocument('invoice.pdf', 'application/pdf');

    expect(document.describe()).toBe('invoice.pdf (application/pdf)');
  });

  it('returns only the name when the document is binary', () => {
    const document = new BaseDocument('scan.bin');

    expect(document.describe()).toBe('scan.bin');
  });
});
