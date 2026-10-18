import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { map } from 'rxjs/operators';
import { Document, DocumentBlock } from '../../domain/base-document';
import { BaseDocumentService } from '../../domain/base-document.service';
import { BaseDocumentStore } from '../../domain/base-document.store';
import { DocumentPortValues } from './document-data-context';
import { DocumentReportViewComponent } from './document-report-view.component';

/**
 * The Preview tab, mounted at `document/<id>/preview`: the *draft* content rendered against data, as the
 * reader will see it once published — the author's check of a report template, and of any document whose
 * ports carry entity data.
 *
 * **Port values are query parameters**, one per port: `?order=<id>` for an `ENTITY_REF`, an RSQL filter for an
 * `ENTITY_COLLECTION`. The parameter bar writes them back to the URL, so a preview of one invoice is a link.
 */
@Component({
  selector: 'pp-document-preview-tab',
  standalone: true,
  imports: [DocumentReportViewComponent, TranslocoPipe],
  template: `
    <div class="pp-document-preview">
      @if (isLoading()) {
        <p>{{ 'base_document.document.content.loading' | transloco }}</p>
      } @else if (loadError(); as error) {
        <p role="alert">{{ error }}</p>
      } @else {
        <h2 class="pp-document-preview__title">{{ title() }}</h2>
        <pp-document-report-view
          [documentId]="entityId()"
          [locale]="locale()"
          [blocks]="blocks()"
          [inputPorts]="inputPorts()"
          [portValues]="portValues()"
          [showParameters]="true"
          [title]="title()"
          (portValuesChange)="onPortValuesChange($event)"
        />
      }
    </div>
  `,
  styles: [
    `
      .pp-document-preview {
        background-color: #ffffff;
        border-radius: 6px;
        padding: 16px 20px 24px;
      }
      .pp-document-preview__title {
        margin: 0 0 8px;
      }
    `,
  ],
})
export class DocumentPreviewTabComponent implements OnInit {
  /** Bound from the route's `:entityId` param, as on the Content tab. */
  readonly entityId = input.required<string>();

  private readonly documentService = inject(BaseDocumentService);
  private readonly store = inject(BaseDocumentStore);
  private readonly transloco = inject(TranslocoService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly locale = signal(this.transloco.getActiveLang());
  protected readonly blocks = signal<DocumentBlock[]>([]);
  protected readonly isLoading = signal(true);
  protected readonly loadError = signal<string | undefined>(undefined);

  private readonly document = computed(() => this.store.currentEntity() as Document | undefined);
  protected readonly title = computed(() => this.document()?.title ?? '');
  protected readonly inputPorts = computed(() => this.document()?.inputPorts ?? []);
  protected readonly portValues = toSignal(this.route.queryParamMap.pipe(map((params) => Object.fromEntries(params.keys.map((key) => [key, params.get(key) ?? undefined])) as DocumentPortValues)), {
    initialValue: {},
  });

  ngOnInit(): void {
    this.store.setCurrentEntity(this.entityId());
    void this.load();
  }

  protected onPortValuesChange(values: DocumentPortValues): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: values, queryParamsHandling: 'merge' });
  }

  /** The draft in the UI language, else in the source locale — the Content tab's choice, with the sign tab's fallback. */
  private async load(): Promise<void> {
    try {
      for (const locale of this.candidateLocales()) {
        try {
          const translation = await this.documentService.getTranslation(this.entityId(), locale);
          this.locale.set(locale);
          this.blocks.set(translation.blocks ?? []);
          return;
        } catch (error) {
          if (!(error instanceof HttpErrorResponse && error.status === 404)) throw error;
        }
      }
    } catch (error) {
      this.loadError.set(error instanceof Error ? error.message : String(error));
    } finally {
      this.isLoading.set(false);
    }
  }

  private candidateLocales(): string[] {
    const sourceLocale = this.document()?.sourceLocale;
    const active = this.transloco.getActiveLang();
    return sourceLocale && sourceLocale !== active ? [active, sourceLocale] : [active];
  }
}
