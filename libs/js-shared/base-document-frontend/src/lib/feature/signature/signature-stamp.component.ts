import { DatePipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { SIGNING_CONTEXT } from './signing-context';

/**
 * The `signature` widget: where a report is signed. Unsigned it is a Sign button with the acceptance hint the
 * Sign tab uses; signed it is a stamp — signer's name in a script face, date, short signature id — which is what
 * the report's PDF shows, and a link to that PDF.
 *
 * **Only in a report.** The signing context comes from `DocumentReportViewComponent`; anywhere else (the editor,
 * a preview with no object) there is nothing to sign for, so the widget draws a disabled placeholder rather than
 * a button that could not work.
 *
 * **Print-safe.** The stamp is plain bordered markup and an SVG line of text — no Material ripple, no shadow —
 * so html2canvas and the browser's print dialog draw it the same. The controls carry `data-html2canvas-ignore`,
 * which keeps the Sign button and the retry link out of the PDF. The signature is set in `cursive`: a web font
 * would be one more thing for the capture to wait for.
 */
@Component({
  selector: 'pp-signature',
  standalone: true,
  imports: [DatePipe, TranslocoPipe],
  template: `
    @if (context?.available()) {
      @if (context!.signature(); as signature) {
        <div class="pp-signature pp-signature--signed" data-testid="signature-stamp">
          @if (stampText()) {
            <div class="pp-signature__text" data-testid="signature-stamp-text">{{ stampText() }}</div>
          }
          <div class="pp-signature__caption">
            {{ label() }}@if (role()) {
              <span> · {{ 'base_document.signature.for_role' | transloco: { role: role() } }}</span>
            }
          </div>
          <svg class="pp-signature__name" role="img" [attr.aria-label]="signature.signedBy" viewBox="0 0 320 44" preserveAspectRatio="xMinYMid meet">
            <text x="0" y="32" font-family="'Segoe Script', 'Brush Script MT', 'Lucida Handwriting', cursive" font-size="30" font-style="italic" fill="currentColor">{{ signature.signedBy }}</text>
          </svg>
          <div class="pp-signature__meta">
            <span data-testid="signature-signer">{{ signature.signedBy }}</span> · <span>{{ signature.signedAt | date: 'medium' }}</span> ·
            <span data-testid="signature-id">{{ shortId(signature.id) }}</span>
          </div>
          @if (signature.pdf) {
            <a class="pp-signature__action" href="#" data-html2canvas-ignore="true" data-testid="signature-pdf-link" (click)="openPdf($event)">{{ signature.pdf.name }}</a>
          } @else if (context!.busy()) {
            <span class="pp-signature__action" data-html2canvas-ignore="true">{{ 'base_document.signature.saving_pdf' | transloco }}</span>
          } @else {
            <button type="button" class="pp-signature__action" data-html2canvas-ignore="true" data-testid="signature-retry-pdf" (click)="context!.retryPdf()">
              {{ 'base_document.signature.pdf_missing' | transloco }}
            </button>
          }
        </div>
      } @else {
        <div class="pp-signature pp-signature--open" data-html2canvas-ignore="true" data-testid="signature-open">
          <div class="pp-signature__caption">
            {{ label() }}@if (role()) {
              <span> · {{ 'base_document.signature.for_role' | transloco: { role: role() } }}</span>
            }
          </div>
          <label class="pp-signature__accept">
            <input type="checkbox" data-testid="signature-accept" [checked]="accepted()" (change)="accepted.set($any($event.target).checked)" />
            {{ 'base_document.document.sign.accept' | transloco }}
          </label>
          <button type="button" class="pp-signature__button" data-testid="signature-sign" [disabled]="!accepted() || context!.busy()" (click)="context!.sign()">
            {{ 'base_document.document.sign.button' | transloco }}
          </button>
        </div>
      }
      @if (context!.error(); as error) {
        <p class="pp-signature__error" role="alert" data-html2canvas-ignore="true">{{ error }}</p>
      }
    } @else {
      <div class="pp-signature pp-signature--placeholder" data-testid="signature-placeholder">
        {{ label() }}: {{ 'base_document.signature.placeholder' | transloco }}
      </div>
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .pp-signature {
        display: inline-flex;
        flex-direction: column;
        gap: 4px;
        min-width: 260px;
        max-width: 360px;
        padding: 10px 14px;
        border: 2px solid var(--pp-surface-sidenav, #186fce);
        border-radius: 6px;
        color: var(--pp-surface-sidenav, #186fce);
        background: #ffffff;
      }
      .pp-signature--open {
        border-style: dashed;
        color: inherit;
      }
      .pp-signature--placeholder {
        border-style: dotted;
        color: rgba(0, 0, 0, 0.54);
      }
      .pp-signature__text {
        font-weight: 700;
        letter-spacing: 0.12em;
        text-transform: uppercase;
      }
      .pp-signature__caption,
      .pp-signature__meta {
        font-size: 12px;
        color: rgba(0, 0, 0, 0.7);
      }
      .pp-signature__name {
        width: 100%;
        height: 44px;
      }
      .pp-signature__accept {
        font-size: 12px;
      }
      .pp-signature__button {
        align-self: flex-start;
        padding: 6px 16px;
        border: 0;
        border-radius: 4px;
        color: var(--pp-button-primary-text, #ffffff);
        background: var(--pp-button-primary-bg, #186fce);
        cursor: pointer;
      }
      .pp-signature__button:disabled {
        opacity: 0.5;
        cursor: default;
      }
      .pp-signature__action {
        align-self: flex-start;
        padding: 0;
        border: 0;
        font-size: 12px;
        color: var(--pp-surface-sidenav, #186fce);
        background: none;
        text-decoration: underline;
        cursor: pointer;
      }
      .pp-signature__error {
        margin: 4px 0 0;
        font-size: 12px;
        color: #b00020;
      }
    `,
  ],
})
export class SignatureStampComponent {
  /** What the signer is called in the caption — "Signed by". */
  readonly label = input('Signed by');
  /** The role the signature is for, shown as "for <role>". */
  readonly role = input<string | undefined>(undefined);
  /** A word across the stamp once signed, e.g. APPROVED. */
  readonly stampText = input<string | undefined>(undefined);

  protected readonly context = inject(SIGNING_CONTEXT, { optional: true });
  protected readonly accepted = signal(false);

  protected readonly shortId = (id: string) => id.slice(0, 8);

  protected openPdf(event: Event): void {
    event.preventDefault();
    this.context?.openPdf();
  }
}
