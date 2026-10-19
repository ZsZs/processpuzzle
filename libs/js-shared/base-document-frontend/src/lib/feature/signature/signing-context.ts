import { InjectionToken, Signal } from '@angular/core';
import { DocumentSignature } from '../../domain/base-document';

/**
 * What a `signature` widget needs from the report it sits in: whether signing is possible there at all, the
 * signature the report has, and the two things a reader can do — sign, and send the PDF again when the first
 * upload failed.
 *
 * Provided by `DocumentReportViewComponent`, the one place that knows the document, the object the report is run
 * for and the page element to capture. Outside a report — the editor, a preview with no subject — nothing
 * provides it, and the widget renders a placeholder instead.
 */
export interface SigningContext {
  /** False until the report has a document and an object to sign for. */
  readonly available: Signal<boolean>;
  /** The newest signature given for this report's document and object, if any. */
  readonly signature: Signal<DocumentSignature | undefined>;
  /** True while signing or uploading, so the button cannot be clicked twice. */
  readonly busy: Signal<boolean>;
  readonly error: Signal<string | undefined>;
  /** Signs, renders the report as a PDF and uploads it; see {@link ReportSigningContext}. */
  sign(): Promise<void>;
  /** Renders and uploads the PDF of an already given signature that has none. */
  retryPdf(): Promise<void>;
  /** Opens the signed PDF in a new tab. */
  openPdf(): void;
}

export const SIGNING_CONTEXT = new InjectionToken<SigningContext>('SIGNING_CONTEXT');
