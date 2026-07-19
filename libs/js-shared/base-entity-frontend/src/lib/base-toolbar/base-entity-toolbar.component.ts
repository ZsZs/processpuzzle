import { Component, computed, inject, input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIcon } from '@angular/material/icon';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltip } from '@angular/material/tooltip';
import { firstValueFrom } from 'rxjs';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { MatFormField, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { BaseFormNavigatorSingletonStore, RouteSegments } from '../base-form-navigator/base-form-navigator.store';
import { BaseUrlSegments } from '../base-form-navigator/base-url-segments';
import { LayoutService } from '@processpuzzle/util';
import { BaseEntityStoreApi } from '../base-entity-store/base-entity.store';
import { BaseEntityQueryComponent } from '../base-query/base-entity-query.component';
import { provideTranslocoScope, TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { PdfExportService } from '../pdf-service/pdf-export.service';
import { PdfExportOptionsDialog, PdfExportDialogResult } from '../pdf-service/pdf-export-options.dialog';
import { entityDescriptorToPdfColumns } from '../pdf-service/entity-descriptor-to-pdf-columns';
import type { PdfExportResult } from '../pdf-service/pdf-export.types';

@Component({
  selector: 'base-entity-toolbar',
  standalone: true,
  imports: [CommonModule, MatToolbarModule, MatIcon, MatFormField, MatInput, MatButton, MatIconButton, MatSuffix, MatMenu, MatMenuItem, MatMenuTrigger, MatTooltip, BaseEntityQueryComponent, TranslocoDirective],
  templateUrl: './base-entity-toolbar.component.html',
  styles: [
    `
      .toolbar-inputs {
        display: flex;
        align-items: center;
        gap: 40px;
      }
      .filter-wrapper {
        display: inline-flex;
        align-items: center;
        gap: 8px;
      }
      .field-label {
        font-size: 14px;
        white-space: nowrap;
      }
    `,
  ],
  providers: [provideTranslocoScope({ scope: 'base_entity', alias: 'base_entity' })],
})
export class BaseEntityToolbarComponent<Entity extends BaseEntity> implements OnInit {
  entityDescriptor = input.required<BaseEntityDescriptor>();
  readonly layoutService = inject(LayoutService);
  protected readonly formNavigator = inject(BaseFormNavigatorSingletonStore);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly pdfExportService = inject(PdfExportService);
  private readonly translocoService = inject(TranslocoService);
  store!: BaseEntityStoreApi<Entity>;
  isDeleteEnabled = computed(() => this.store?.selectedEntities().length != 0 && !this.entityDescriptor().isAbstract);
  isEditEnabled = computed(() => this.store?.selectedEntities().length == 1 && !this.entityDescriptor().isAbstract);
  isNewEnabled = computed(() => !this.entityDescriptor().isAbstract);
  isPdfExportEnabled = computed(() => (this.store?.entities().length ?? 0) > 0);
  isExporting = this.pdfExportService.exporting;

  // region Angular lifecycle hooks
  ngOnInit(): void {
    this.store = this.entityDescriptor().store as BaseEntityStoreApi<Entity>;
    this.formNavigator.setEntityName(this.entityDescriptor().entityName);
  }

  // endregion

  // event handling methods
  onAddEntity() {
    this.formNavigator.navigateToDetails(this.entityDescriptor().entityName, BaseUrlSegments.NewEntity);
  }

  onDeleteEntities() {
    this.store.selectedEntities().forEach((entity) => {
      if (entity.id) void this.store.delete(entity.id);
    });
  }

  onEditEntity() {
    const entityId = this.store.currentEntity()?.id;
    if (entityId) this.formNavigator.navigateToDetails(this.entityDescriptor().entityName, entityId);
  }

  async onExportPdf(): Promise<void> {
    const dialogResult = await firstValueFrom(this.dialog.open<PdfExportOptionsDialog, unknown, PdfExportDialogResult | undefined>(PdfExportOptionsDialog, { width: '360px', autoFocus: false }).afterClosed());
    if (!dialogResult) return; // user cancelled

    const columns = entityDescriptorToPdfColumns(this.entityDescriptor().attrDescriptors);
    const entities = this.store.entities() as unknown as Record<string, unknown>[];
    const result = await this.pdfExportService.export(entities, columns, {
      ...dialogResult,
      title: this.resolveTitle(),
      subtitle: this.translocoService.translate('base_entity.pdf_export.subtitle', { count: entities.length }),
      filename: this.entityDescriptor().entityName.toLowerCase() + '-export',
    });
    this.notifyExportResult(result);
  }

  onDoFilter($event: KeyboardEvent) {
    const filterValue = ($event.target as HTMLInputElement).value;
    this.store.doFilter(filterValue);
  }

  onClearFilter(input: HTMLInputElement) {
    input.value = '';
    this.store.doFilter('');
    this.store.load({});
  }

  // endregion

  // public queries and mutators
  isSmallDevice(): boolean {
    return this.layoutService.isSmallDevice();
  }

  // endregion

  // protected, private helper methods
  private resolveTitle(): string {
    const entityTitle = this.entityDescriptor().entityTitle;
    const title = typeof entityTitle === 'function' ? entityTitle() : entityTitle;
    return title || this.entityDescriptor().entityName;
  }

  private notifyExportResult(result: PdfExportResult): void {
    const message = result.success ? this.translocoService.translate<string>('base_entity.pdf_export.success', { count: result.rowCount }) : (result.error ?? this.translocoService.translate<string>('base_entity.pdf_export.failure'));
    this.snackBar.open(message, undefined, { duration: 4000 });
  }

  protected readonly RouteSegments = RouteSegments;
  // endregion
}
