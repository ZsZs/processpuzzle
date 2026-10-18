import { Component, effect, ElementRef, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatOption, MatSelect } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';
import { DocumentBlock, DocumentInputPort, PortType } from '../../domain/base-document';
import { DocumentReaderComponent } from '../document-sign/document-reader.component';
import { DocumentDataContext, DocumentObjectOption, DocumentPortValues } from './document-data-context';
import { printElement } from './print-element';

/**
 * One document rendered against data: the content, read-only, with every port path resolved — a report, when
 * the document is a report template. What the Preview tab and an entity's Reports tab both show.
 *
 * The port values come in from the host (`portValues`), because what they are depends on where the report is
 * opened from: the URL's query parameters on the Preview tab, the current object on the Reports tab. With
 * `showParameters`, an object picker per `ENTITY_REF` port lets the reader change them, and each change is
 * handed back through `portValuesChange` for the host to make it stick.
 */
@Component({
  selector: 'pp-document-report-view',
  standalone: true,
  imports: [DocumentReaderComponent, MatButton, MatFormField, MatIcon, MatLabel, MatOption, MatSelect, TranslocoPipe],
  providers: [DocumentDataContext],
  template: `
    <div class="pp-report-view">
      <div class="pp-report-view__bar">
        @if (showParameters()) {
          @for (port of entityRefPorts(); track port.name) {
            <mat-form-field subscriptSizing="dynamic">
              <mat-label>{{ port.name }}</mat-label>
              <mat-select [attr.data-testid]="'report-parameter-' + port.name" [value]="portValues()[port.name]" (valueChange)="onParameterChange(port.name, $event)">
                @for (option of options()[port.name] ?? []; track option.id) {
                  <mat-option [value]="option.id">{{ option.label }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          }
        }
        <span class="pp-report-view__spacer"></span>
        <button mat-stroked-button type="button" data-testid="report-print" [disabled]="data.loading()" (click)="onPrint()">
          <mat-icon>print</mat-icon>
          {{ 'base_document.document.report.print' | transloco }}
        </button>
      </div>

      @if (data.missingRequiredPorts().length > 0) {
        <p class="pp-report-view__notice" data-testid="report-missing-parameters">
          {{ 'base_document.document.report.missing_parameters' | transloco: { ports: data.missingRequiredPorts().join(', ') } }}
        </p>
      }
      @if (data.error(); as error) {
        <p class="pp-report-view__notice" role="alert">{{ error }}</p>
      }

      <div #page class="pp-report-view__page" data-testid="report-page">
        <pp-document-reader [documentId]="documentId()" [locale]="locale()" [blocks]="blocks()" />
      </div>
    </div>
  `,
  styles: [
    `
      .pp-report-view__bar {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 8px;
        margin-bottom: 12px;
      }
      .pp-report-view__spacer {
        flex: 1;
      }
      .pp-report-view__notice {
        margin: 0 0 12px;
        color: #8a5300;
      }
      .pp-report-view__page {
        background: #ffffff;
      }
    `,
  ],
})
export class DocumentReportViewComponent {
  readonly documentId = input.required<string>();
  readonly locale = input.required<string>();
  readonly blocks = input.required<DocumentBlock[]>();
  readonly inputPorts = input<readonly DocumentInputPort[]>([]);
  readonly portValues = input<DocumentPortValues>({});
  readonly showParameters = input(false);
  /** The title the printed page carries — the browser offers it as the PDF's file name. */
  readonly title = input('');
  readonly portValuesChange = output<DocumentPortValues>();

  protected readonly data = inject(DocumentDataContext);
  protected readonly options = signal<Readonly<Record<string, DocumentObjectOption[]>>>({});
  protected readonly entityRefPorts = signal<DocumentInputPort[]>([]);
  private readonly page = viewChild.required<ElementRef<HTMLElement>>('page');

  constructor() {
    // Rebinding writes the context's signals, so it runs untracked: only the ports and values are dependencies.
    effect(() => {
      const ports = this.inputPorts();
      const values = this.portValues();
      untracked(() => void this.data.bind(ports, values));
    });
    effect(() => {
      const ports = this.inputPorts().filter((port) => port.type === PortType.ENTITY_REF);
      const showParameters = this.showParameters();
      untracked(() => {
        this.entityRefPorts.set(ports);
        if (showParameters) void this.loadOptions(ports);
      });
    });
  }

  protected onParameterChange(port: string, id: string): void {
    this.portValuesChange.emit({ ...this.portValues(), [port]: id });
  }

  protected onPrint(): void {
    printElement(this.page().nativeElement, this.title());
  }

  private async loadOptions(ports: DocumentInputPort[]): Promise<void> {
    try {
      const entries = await Promise.all(ports.map(async (port) => [port.name, await this.data.listObjects(port.entityType)] as const));
      this.options.set(Object.fromEntries(entries));
    } catch {
      // An empty picker says it well enough; the global HTTP error handler has already reported the failure.
      this.options.set({});
    }
  }
}
