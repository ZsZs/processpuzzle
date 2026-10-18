import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal, Signal, untracked } from '@angular/core';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatOption, MatSelect } from '@angular/material/select';
import { ROUTER_OUTLET_DATA } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { DocumentBlock } from '../../domain/base-document';
import { BaseDocumentService } from '../../domain/base-document.service';
import { DocumentPortValues } from './document-data-context';
import { DocumentReportViewComponent } from './document-report-view.component';
import { EntityReportRegistry, EntityReportTemplate } from './entity-report.registry';

/**
 * An entity's Reports tab, mounted at `<entity>/<id>/reports`: the *published* report templates that report on
 * this kind of object, run for this object — *Print invoice* on an Order. The object goes into the template's
 * `ENTITY_REF` port of the entity's type; with more than one template, a picker chooses which one is shown.
 */
@Component({
  selector: 'pp-entity-reports-tab',
  standalone: true,
  imports: [DocumentReportViewComponent, MatFormField, MatLabel, MatOption, MatSelect, TranslocoPipe],
  template: `
    <div class="pp-entity-reports">
      @if (templates().length > 1) {
        <mat-form-field subscriptSizing="dynamic">
          <mat-label>{{ 'base_document.document.report.template' | transloco }}</mat-label>
          <mat-select data-testid="entity-reports-template" [value]="selected()?.document?.id" (valueChange)="onSelect($event)">
            @for (template of templates(); track template.document.id) {
              <mat-option [value]="template.document.id">{{ template.document.title }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      }
      @if (isLoading()) {
        <p>{{ 'base_document.document.content.loading' | transloco }}</p>
      } @else if (notPublished()) {
        <p data-testid="entity-reports-not-published">{{ 'base_document.document.report.not_published' | transloco }}</p>
      } @else if (loadError(); as error) {
        <p role="alert">{{ error }}</p>
      } @else if (selected(); as template) {
        <pp-document-report-view
          [documentId]="template.document.id ?? ''"
          [locale]="locale()"
          [blocks]="blocks()"
          [inputPorts]="template.document.inputPorts"
          [portValues]="portValues()"
          [title]="template.document.title"
        />
      }
    </div>
  `,
  styles: [
    `
      .pp-entity-reports {
        background-color: #ffffff;
        border-radius: 6px;
        padding: 16px 20px 24px;
      }
    `,
  ],
})
export class EntityReportsTabComponent {
  /** Bound from the route's `:entityId` param — the object the report is run for. */
  readonly entityId = input.required<string>();

  private readonly outletData = inject(ROUTER_OUTLET_DATA, { optional: true }) as Signal<BaseEntityDescriptor | undefined> | null;
  private readonly registry = inject(EntityReportRegistry);
  private readonly documentService = inject(BaseDocumentService);
  private readonly transloco = inject(TranslocoService);

  protected readonly templates = signal<EntityReportTemplate[]>([]);
  protected readonly selected = signal<EntityReportTemplate | undefined>(undefined);
  protected readonly locale = signal(this.transloco.getActiveLang());
  protected readonly blocks = signal<DocumentBlock[]>([]);
  protected readonly isLoading = signal(true);
  protected readonly notPublished = signal(false);
  protected readonly loadError = signal<string | undefined>(undefined);
  protected readonly portValues = computed<DocumentPortValues>(() => {
    const template = this.selected();
    return template ? { [template.port.name]: this.entityId() } : {};
  });

  constructor() {
    effect(() => {
      const entityName = this.outletData?.()?.entityName;
      untracked(() => void this.loadTemplates(entityName));
    });
  }

  protected onSelect(documentId: string): void {
    const template = this.templates().find((candidate) => candidate.document.id === documentId);
    if (template) void this.show(template);
  }

  private async loadTemplates(entityName: string | undefined): Promise<void> {
    const templates = await this.registry.templatesFor(entityName);
    this.templates.set(templates);
    if (templates.length > 0) await this.show(templates[0]);
    else this.isLoading.set(false);
  }

  /** The published translation in the UI language, else in the template's source locale. */
  private async show(template: EntityReportTemplate): Promise<void> {
    this.selected.set(template);
    this.isLoading.set(true);
    this.notPublished.set(false);
    this.loadError.set(undefined);
    try {
      const active = this.transloco.getActiveLang();
      const source = template.document.sourceLocale;
      for (const locale of source && source !== active ? [active, source] : [active]) {
        try {
          const translation = await this.documentService.getTranslation(template.document.id ?? '', locale, false);
          this.locale.set(locale);
          this.blocks.set(translation.blocks ?? []);
          return;
        } catch (error) {
          if (!(error instanceof HttpErrorResponse && error.status === 404)) throw error;
        }
      }
      this.notPublished.set(true);
    } catch (error) {
      this.loadError.set(error instanceof Error ? error.message : String(error));
    } finally {
      this.isLoading.set(false);
    }
  }
}
