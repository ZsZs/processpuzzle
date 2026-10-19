import { ApplicationRef, inject, Injectable, signal } from '@angular/core';
import { ObjectStoreService } from '@processpuzzle/base-entity';
import { DocumentSignature, DocumentSignatureSubject } from '../../domain/base-document';
import { BaseDocumentService } from '../../domain/base-document.service';
import { PDF_RENDERER } from '../report/report-pdf';
import { SigningContext } from './signing-context';

/** What the report view tells its signing context about the report it shows. */
export interface ReportSigningTarget {
  documentId: string;
  locale: string;
  subject: DocumentSignatureSubject | undefined;
  fileName: string;
  /** The element to capture; read when the PDF is made, not before. */
  page: () => HTMLElement;
}

/**
 * Signing a report is two phases, so that the stamp in the PDF shows what the server recorded — signer, time,
 * signature id — rather than what the browser guessed:
 *
 * 1. `POST .../signatures` — the signature exists, and the widget re-renders as the stamp;
 * 2. the report is captured as a PDF and `PUT .../signatures/{id}/pdf` stores it.
 *
 * A failure in the second phase leaves a signature without a PDF, which the stamp says ("PDF missing") and
 * {@link retryPdf} repairs; the server allows one PDF per signature and only from the signer.
 */
@Injectable()
export class ReportSigningContext implements SigningContext {
  private readonly documents = inject(BaseDocumentService);
  private readonly objects = inject(ObjectStoreService);
  private readonly appRef = inject(ApplicationRef);
  private readonly renderPdf = inject(PDF_RENDERER);

  readonly available = signal(false);
  readonly signature = signal<DocumentSignature | undefined>(undefined);
  readonly busy = signal(false);
  readonly error = signal<string | undefined>(undefined);

  private target: ReportSigningTarget | undefined;
  private generation = 0;

  /** Called whenever the report's document, locale or object changes: forgets the old signature and loads the new one. */
  async bind(target: ReportSigningTarget | undefined): Promise<void> {
    const generation = ++this.generation;
    this.target = target;
    this.signature.set(undefined);
    this.error.set(undefined);
    this.available.set(target?.subject !== undefined && target.documentId !== '');
    if (!target?.subject || !target.documentId) return;
    try {
      const signatures = await this.documents.listSignatures(target.documentId, target.subject);
      if (generation === this.generation) this.signature.set(signatures[0]);
    } catch {
      // No signatures could be read, so the widget offers Sign; the server answers an existing signature then.
    }
  }

  async sign(): Promise<void> {
    const target = this.target;
    const subject = target?.subject;
    if (!target || !subject || this.busy()) return;
    await this.run(async () => {
      const signature = await this.documents.sign(target.documentId, subject, target.locale);
      this.signature.set(signature);
      if (!signature.pdf) await this.uploadPdf(target, signature);
    });
  }

  async retryPdf(): Promise<void> {
    const target = this.target;
    const signature = this.signature();
    if (!target || !signature || signature.pdf || this.busy()) return;
    await this.run(() => this.uploadPdf(target, signature));
  }

  openPdf(): void {
    const pdf = this.signature()?.pdf;
    if (!pdf) return;
    this.objects.getObjectByID(pdf.bucket, pdf.objectId).subscribe({
      next: (response) => {
        const url = URL.createObjectURL(response.data);
        window.open(url, '_blank', 'noopener');
        setTimeout(() => URL.revokeObjectURL(url), 60000);
      },
      error: (error_) => this.error.set(error_ instanceof Error ? error_.message : String(error_)),
    });
  }

  private async uploadPdf(target: ReportSigningTarget, signature: DocumentSignature): Promise<void> {
    // The stamp has to be in the DOM before the page is captured; rendering now beats guessing a delay.
    this.appRef.tick();
    const pdf = await this.renderPdf(target.page());
    this.signature.set(await this.documents.attachPdf(target.documentId, signature.id, pdf, target.fileName));
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set(undefined);
    try {
      await action();
    } catch (error_) {
      this.error.set(error_ instanceof Error ? error_.message : String(error_));
    } finally {
      this.busy.set(false);
    }
  }
}
