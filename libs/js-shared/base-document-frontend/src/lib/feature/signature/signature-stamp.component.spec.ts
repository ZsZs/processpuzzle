import { signal, WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { describe, expect, it, vi } from 'vitest';
import { DocumentSignature } from '../../domain/base-document';
import { SignatureStampComponent } from './signature-stamp.component';
import { SIGNING_CONTEXT, SigningContext } from './signing-context';

const SIGNATURE: DocumentSignature = {
  id: 'abcdef12-0000-0000-0000-000000000000',
  documentId: 'd-1',
  locale: 'en',
  revision: 1,
  contentHash: 'h',
  subjectType: 'Order',
  subjectId: 'o-1',
  signedBy: 'Ada Lovelace',
  signedAt: '2026-10-19T09:00:00Z',
};
const PDF = { bucket: 'b', objectId: 'o', name: 'order-invoice-1.pdf', mimeType: 'application/pdf' };

interface TestContext extends SigningContext {
  signature: WritableSignal<DocumentSignature | undefined>;
  busy: WritableSignal<boolean>;
}

function context(signature?: DocumentSignature): TestContext {
  return {
    available: signal(true),
    signature: signal(signature),
    busy: signal(false),
    error: signal<string | undefined>(undefined),
    sign: vi.fn(async () => undefined),
    retryPdf: vi.fn(async () => undefined),
    openPdf: vi.fn(),
  };
}

function render(signing: SigningContext | null, inputs: Record<string, unknown> = {}) {
  TestBed.configureTestingModule({
    providers: [provideTranslocoTesting({ translations: { en: { 'base_document.signature.pdf_missing': 'PDF missing — retry' } } }), ...(signing ? [{ provide: SIGNING_CONTEXT, useValue: signing }] : [])],
  });
  const fixture = TestBed.createComponent(SignatureStampComponent);
  for (const [name, value] of Object.entries(inputs)) fixture.componentRef.setInput(name, value);
  fixture.detectChanges();
  return { fixture, root: fixture.nativeElement as HTMLElement };
}

const byId = <T extends HTMLElement>(root: HTMLElement, id: string) => root.querySelector<T>(`[data-testid="${id}"]`);

describe('SignatureStampComponent', () => {
  it('is a placeholder outside a report', () => {
    const { root } = render(null);

    expect(byId(root, 'signature-placeholder')).not.toBeNull();
    expect(byId(root, 'signature-sign')).toBeNull();
  });

  it('offers Sign only once the reader has accepted', () => {
    const signing = context();
    const { fixture, root } = render(signing);
    const button = byId<HTMLButtonElement>(root, 'signature-sign')!;
    expect(button.disabled).toBe(true);

    const accept = byId<HTMLInputElement>(root, 'signature-accept')!;
    accept.checked = true;
    accept.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    button.click();

    expect(button.disabled).toBe(false);
    expect(signing.sign).toHaveBeenCalledOnce();
  });

  it('shows the signer, the short signature id and the stamp text once signed — and no button', () => {
    const { root } = render(context({ ...SIGNATURE, pdf: PDF }), { stampText: 'APPROVED', role: 'Order Manager' });

    expect(byId(root, 'signature-signer')?.textContent).toBe('Ada Lovelace');
    expect(byId(root, 'signature-id')?.textContent).toBe('abcdef12');
    expect(byId(root, 'signature-stamp-text')?.textContent).toContain('APPROVED');
    expect(byId(root, 'signature-sign')).toBeNull();
    expect(byId(root, 'signature-pdf-link')?.textContent).toContain('order-invoice-1.pdf');
  });

  it('opens the stored PDF through the context', () => {
    const signing = context({ ...SIGNATURE, pdf: PDF });
    const { root } = render(signing);

    byId(root, 'signature-pdf-link')!.click();

    expect(signing.openPdf).toHaveBeenCalledOnce();
  });

  it('says the PDF is missing, and retries it', () => {
    const signing = context(SIGNATURE);
    const { root } = render(signing);

    const retry = byId(root, 'signature-retry-pdf')!;
    expect(retry.textContent).toContain('PDF missing');
    retry.click();

    expect(signing.retryPdf).toHaveBeenCalledOnce();
  });

  it('keeps the controls out of the PDF capture', () => {
    const { root } = render(context(SIGNATURE));

    expect(byId(root, 'signature-retry-pdf')?.getAttribute('data-html2canvas-ignore')).toBe('true');
  });
});
