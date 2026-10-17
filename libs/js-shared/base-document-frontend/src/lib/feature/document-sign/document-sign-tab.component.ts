import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatIcon } from '@angular/material/icon';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Document, DocumentBlock, DocumentSignature, DocumentSignatureSubject } from '../../domain/base-document';
import { BaseDocumentService } from '../../domain/base-document.service';
import { BaseDocumentStore } from '../../domain/base-document.store';
import { DocumentReaderComponent } from './document-reader.component';

/**
 * The Sign tab, mounted at `document/<id>/sign`: the document's *published* content, read-only, and — when
 * the link says whom the signature is for — an "I accept" box and a Sign button.
 *
 * **Whom it is signed for comes from the link** — `?subjectType=employee&subjectId=<id>` — because one
 * document serves many objects and nothing on this screen could know which one is meant. The task dashboard
 * builds that link from the run's subject; opened without it, the tab is a reader with the signature list and
 * no button, since a signature for nobody in particular proves nothing.
 *
 * **The tick box is the act of acceptance.** A signature records that its signer accepted what they read, so
 * the button is enabled only once they have said so, and it signs the revision that was shown — the server
 * signs the current publication, which is the one this tab just loaded.
 *
 * Who signs is not chosen here at all: the server takes it from the token.
 */
@Component({
  selector: 'pp-document-sign-tab',
  standalone: true,
  imports: [DatePipe, DocumentReaderComponent, MatButton, MatCheckbox, MatIcon, TranslocoPipe],
  template: `
    <div class="pp-document-sign">
      @if (isLoading()) {
        <p>{{ 'base_document.document.sign.loading' | transloco }}</p>
      } @else if (notPublished()) {
        <p data-testid="document-sign-not-published">{{ 'base_document.document.sign.not_published' | transloco }}</p>
      } @else if (loadError(); as error) {
        <p role="alert">{{ error }}</p>
      } @else {
        <h2 class="pp-document-sign__title">{{ title() }}</h2>
        <pp-document-reader [documentId]="entityId()" [locale]="locale()" [blocks]="blocks()" />

        <section class="pp-document-sign__panel" data-testid="document-sign-panel">
          @if (subject()) {
            @if (ownSignature(); as signature) {
              <p class="pp-document-sign__done" data-testid="document-signed">
                <mat-icon>verified</mat-icon>
                {{ 'base_document.document.sign.signed' | transloco: { signer: signature.signedBy, date: (signature.signedAt | date: 'medium') } }}
              </p>
            } @else {
              <mat-checkbox data-testid="document-sign-accept" [checked]="accepted()" (change)="accepted.set($event.checked)">
                {{ 'base_document.document.sign.accept' | transloco }}
              </mat-checkbox>
              <button mat-flat-button type="button" data-testid="document-sign-button" [disabled]="!accepted() || isSigning()" (click)="onSign()">
                <mat-icon>draw</mat-icon>
                {{ 'base_document.document.sign.button' | transloco }}
              </button>
            }
            @if (signError(); as error) {
              <p class="pp-document-sign__error" role="alert">{{ error }}</p>
            }
          }

          <h3>{{ 'base_document.document.sign.signatures' | transloco }}</h3>
          @if (signatures().length === 0) {
            <p data-testid="document-sign-none">{{ 'base_document.document.sign.none' | transloco }}</p>
          } @else {
            <ul class="pp-document-sign__list">
              @for (signature of signatures(); track signature.id) {
                <li [attr.data-testid]="'document-signature-' + signature.id">
                  {{ signature.signedBy }} · {{ signature.signedAt | date: 'medium' }} · {{ signature.subjectType }} {{ signature.subjectId }} · r{{ signature.revision }}
                </li>
              }
            </ul>
          }
        </section>
      }
    </div>
  `,
  styles: [
    `
      .pp-document-sign {
        background-color: #ffffff;
        border-radius: 6px;
        padding: 16px 20px 24px;
      }
      .pp-document-sign__title {
        margin: 0 0 8px;
      }
      .pp-document-sign__panel {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 8px;
        margin-top: 24px;
        padding-top: 16px;
        border-top: 1px solid rgba(0, 0, 0, 0.12);
      }
      .pp-document-sign__done {
        display: flex;
        align-items: center;
        gap: 6px;
      }
      .pp-document-sign__error {
        color: #b00020;
      }
      .pp-document-sign__list {
        margin: 0;
        padding-left: 20px;
      }
    `,
  ],
})
export class DocumentSignTabComponent implements OnInit {
  /** Bound from the route's `:entityId` param, as on the Content tab. */
  readonly entityId = input.required<string>();
  /** Bound from the `subjectType` query parameter — see the class comment. */
  readonly subjectType = input<string | undefined>(undefined);
  /** Bound from the `subjectId` query parameter. */
  readonly subjectId = input<string | undefined>(undefined);

  private readonly documentService = inject(BaseDocumentService);
  private readonly store = inject(BaseDocumentStore);
  private readonly transloco = inject(TranslocoService);

  protected readonly locale = signal(this.transloco.getActiveLang());
  protected readonly blocks = signal<DocumentBlock[]>([]);
  protected readonly signatures = signal<DocumentSignature[]>([]);
  protected readonly isLoading = signal(true);
  protected readonly notPublished = signal(false);
  protected readonly loadError = signal<string | undefined>(undefined);
  protected readonly accepted = signal(false);
  protected readonly isSigning = signal(false);
  protected readonly signError = signal<string | undefined>(undefined);
  /** The signature this session just gave, or already had — shown instead of the button. */
  protected readonly ownSignature = signal<DocumentSignature | undefined>(undefined);

  protected readonly title = computed(() => (this.store.currentEntity() as Document | undefined)?.title ?? '');
  protected readonly subject = computed<DocumentSignatureSubject | undefined>(() => {
    const subjectType = this.subjectType();
    const subjectId = this.subjectId();
    return subjectType && subjectId ? { subjectType, subjectId } : undefined;
  });

  ngOnInit(): void {
    this.store.setCurrentEntity(this.entityId());
    void this.load();
  }

  protected async onSign(): Promise<void> {
    const subject = this.subject();
    if (!subject) return;
    this.isSigning.set(true);
    this.signError.set(undefined);
    try {
      const signature = await this.documentService.sign(this.entityId(), subject, this.locale());
      this.ownSignature.set(signature);
      this.signatures.update((list) => [signature, ...list.filter((existing) => existing.id !== signature.id)]);
    } catch (error) {
      this.signError.set(messageOf(error));
    } finally {
      this.isSigning.set(false);
    }
  }

  private async load(): Promise<void> {
    try {
      await this.loadPublishedContent();
      if (!this.notPublished()) {
        this.signatures.set(await this.documentService.listSignatures(this.entityId(), this.subject()));
      }
    } catch (error) {
      this.loadError.set(messageOf(error));
    } finally {
      this.isLoading.set(false);
    }
  }

  /**
   * The published translation in the UI language, else in the document's source locale — the same fallback
   * the public read makes, so a reader whose language is not translated yet still has something to sign.
   */
  private async loadPublishedContent(): Promise<void> {
    for (const locale of this.candidateLocales()) {
      try {
        const translation = await this.documentService.getTranslation(this.entityId(), locale, false);
        this.locale.set(locale);
        this.blocks.set(translation.blocks ?? []);
        return;
      } catch (error) {
        if (!(error instanceof HttpErrorResponse && error.status === 404)) throw error;
      }
    }
    this.notPublished.set(true);
  }

  private candidateLocales(): string[] {
    const sourceLocale = (this.store.currentEntity() as Document | undefined)?.sourceLocale;
    const active = this.transloco.getActiveLang();
    return sourceLocale && sourceLocale !== active ? [active, sourceLocale] : [active];
  }
}

function messageOf(error: unknown): string {
  if (error instanceof HttpErrorResponse) return error.error?.errorText ?? error.message;
  return error instanceof Error ? error.message : String(error);
}
