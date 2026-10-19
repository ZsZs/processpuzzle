import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ObjectStoreService } from '@processpuzzle/base-entity';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DocumentSignature } from '../../domain/base-document';
import { BaseDocumentService } from '../../domain/base-document.service';
import { PDF_RENDERER } from '../report/report-pdf';
import { ReportSigningContext, ReportSigningTarget } from './report-signing-context';

const renderPdf = vi.fn();

const SIGNATURE: DocumentSignature = {
  id: 's-1',
  documentId: 'd-1',
  locale: 'en',
  revision: 1,
  contentHash: 'h',
  subjectType: 'Order',
  subjectId: 'o-1',
  signedBy: 'Ada',
  signedAt: '2026-10-19T09:00:00Z',
};
const WITH_PDF: DocumentSignature = { ...SIGNATURE, pdf: { bucket: 'b', objectId: 'x', name: 'f.pdf', mimeType: 'application/pdf' } };

describe('ReportSigningContext', () => {
  const documents = { listSignatures: vi.fn(), sign: vi.fn(), attachPdf: vi.fn() };
  const page = document.createElement('div');
  const target: ReportSigningTarget = { documentId: 'd-1', locale: 'en', subject: { subjectType: 'Order', subjectId: 'o-1' }, fileName: 'f.pdf', page: () => page };
  let context: ReportSigningContext;

  beforeEach(() => {
    vi.resetAllMocks();
    documents.listSignatures.mockResolvedValue([]);
    renderPdf.mockResolvedValue(new Blob(['%PDF'], { type: 'application/pdf' }));
    TestBed.configureTestingModule({
      providers: [
        ReportSigningContext,
        { provide: BaseDocumentService, useValue: documents },
        { provide: ObjectStoreService, useValue: {} },
        { provide: PDF_RENDERER, useValue: renderPdf },
        { provide: ApplicationRef, useValue: { tick: vi.fn() } },
      ],
    });
    context = TestBed.inject(ReportSigningContext);
  });

  it('is unavailable without an object to sign for', async () => {
    await context.bind({ ...target, subject: undefined });

    expect(context.available()).toBe(false);
    expect(documents.listSignatures).not.toHaveBeenCalled();
  });

  it('shows the newest existing signature of the document and object', async () => {
    documents.listSignatures.mockResolvedValue([WITH_PDF]);

    await context.bind(target);

    expect(documents.listSignatures).toHaveBeenCalledWith('d-1', target.subject);
    expect(context.available()).toBe(true);
    expect(context.signature()).toEqual(WITH_PDF);
  });

  it('signs, renders the page and uploads it as the signature’s PDF', async () => {
    documents.sign.mockResolvedValue(SIGNATURE);
    documents.attachPdf.mockResolvedValue(WITH_PDF);
    await context.bind(target);

    await context.sign();

    expect(documents.sign).toHaveBeenCalledWith('d-1', target.subject, 'en');
    expect(renderPdf).toHaveBeenCalledWith(page);
    expect(documents.attachPdf).toHaveBeenCalledWith('d-1', 's-1', expect.any(Blob), 'f.pdf');
    expect(context.signature()).toEqual(WITH_PDF);
    expect(context.busy()).toBe(false);
    expect(context.error()).toBeUndefined();
  });

  it('does not upload again for a signature that already has its PDF', async () => {
    documents.sign.mockResolvedValue(WITH_PDF);
    await context.bind(target);

    await context.sign();

    expect(renderPdf).not.toHaveBeenCalled();
    expect(documents.attachPdf).not.toHaveBeenCalled();
  });

  it('keeps the signature when the upload fails, and a retry sends the PDF only', async () => {
    documents.sign.mockResolvedValue(SIGNATURE);
    documents.attachPdf.mockRejectedValueOnce(new Error('storage down')).mockResolvedValueOnce(WITH_PDF);
    await context.bind(target);

    await context.sign();

    expect(context.signature()).toEqual(SIGNATURE);
    expect(context.error()).toBe('storage down');

    await context.retryPdf();

    expect(documents.sign).toHaveBeenCalledOnce();
    expect(context.signature()).toEqual(WITH_PDF);
    expect(context.error()).toBeUndefined();
  });

  it('reports a failed signing and stays unsigned', async () => {
    documents.sign.mockRejectedValue(new Error('403'));
    await context.bind(target);

    await context.sign();

    expect(context.signature()).toBeUndefined();
    expect(context.error()).toBe('403');
    expect(context.busy()).toBe(false);
  });
});
