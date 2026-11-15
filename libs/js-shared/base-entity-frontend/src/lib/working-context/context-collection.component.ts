import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatCell, MatCellDef, MatColumnDef, MatHeaderCell, MatHeaderCellDef, MatHeaderRow, MatHeaderRowDef, MatRow, MatRowDef, MatTable } from '@angular/material/table';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { httpErrorMessage, injectActiveLang } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { createTestId } from '../base-entity/base-entity-utility';
import { BaseUrlSegments } from '../base-form-navigator/base-url-segments';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { effectiveDateFormat, formatDateValue } from '../base-entity/date-format';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { filterAttributeDescriptors } from '../base-entity/filter-attr-descriptor';
import { BaseEntityDescriptorRegistry } from '../base-entity-facade/base-entity-descriptor.registry';
import { DeleteConfirmationDialog, DeleteConfirmationDialogData } from '../dialogs/delete-confirmation.dialog';
import { EntityLabelPipe } from '../i18n/entity-label.pipe';
import { BaseFormNavigatorSingletonStore, snakeCaseName } from '../base-form-navigator/base-form-navigator.store';
import { rowId } from '../base-entity-embedded/embedded-aggregate';
import { CollectionItem, EmbeddedCollectionService, itemVersionOf } from './embedded-collection.service';
import { WorkingContextService } from './working-context.service';

/** Route data: the context the collection belongs to. */
export const CONTEXT_COLLECTION_CONTEXT_KEY = 'collectionContext';
/** Route data: the attribute of the context's entity that carries the collection. */
export const CONTEXT_COLLECTION_PATH_KEY = 'collectionPath';
/** Route data: the embedded entity type of the collection's items. */
export const CONTEXT_COLLECTION_ENTITY_KEY = 'collectionEntity';

/**
 * A list of the embedded collection of the **active** entity of a working context — the zones of the active
 * warehouse — with add, edit and remove acting on that entity alone: the user never selects a warehouse here, and a change sends
 * the item rather than the warehouse (see {@link EmbeddedCollectionService}).
 *
 * Everything comes from metadata: the route data names the context, the attribute and the item type, and the item
 * type's descriptor gives the columns and, in the dialog, the form. Without an active context the route never gets here —
 * `workingContextRoutes` shows its empty state instead.
 */
@Component({
  selector: 'pp-context-collection',
  standalone: true,
  imports: [
    MatTable,
    MatColumnDef,
    MatHeaderCell,
    MatHeaderCellDef,
    MatCell,
    MatCellDef,
    MatHeaderRow,
    MatHeaderRowDef,
    MatRow,
    MatRowDef,
    MatButton,
    MatIconButton,
    MatIcon,
    MatProgressBar,
    TranslocoPipe,
    EntityLabelPipe,
  ],
  template: `
    @if (isLoading()) {
      <mat-progress-bar mode="query" />
    }
    <div class="collection-toolbar">
      <button type="button" mat-flat-button [attr.data-testid]="testId('add')" [disabled]="!canChange()" (click)="onAdd()">
        <mat-icon>add</mat-icon>{{ 'base_entity.context_collection.add' | transloco }}
      </button>
    </div>
    @if (error(); as message) {
      <p class="collection-error" role="alert" [attr.data-testid]="testId('error')">{{ message }}</p>
    }
    <table mat-table [dataSource]="rows()" [attr.data-testid]="testId('table')">
      @for (column of columns(); track column.attrName) {
        <ng-container [matColumnDef]="column.attrName">
          <th mat-header-cell *matHeaderCellDef>{{ column.i18nKey() | ppLabel: column.label }}</th>
          <td mat-cell *matCellDef="let row">{{ display(column, row) }}</td>
        </ng-container>
      }
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let row" class="collection-actions">
          <button type="button" mat-icon-button [attr.data-testid]="testId('edit')" [attr.aria-label]="'base_entity.context_collection.edit' | transloco" (click)="onEdit(row)">
            <mat-icon>edit</mat-icon>
          </button>
          <button type="button" mat-icon-button [attr.data-testid]="testId('delete')" [attr.aria-label]="'base_entity.context_collection.delete' | transloco" (click)="onDelete(row)">
            <mat-icon>delete</mat-icon>
          </button>
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="displayedColumns()"></tr>
      <tr mat-row *matRowDef="let row; columns: displayedColumns()"></tr>
    </table>
    @if (!isLoading() && rows().length === 0) {
      <p class="collection-empty" [attr.data-testid]="testId('empty')">{{ 'base_entity.context_collection.empty' | transloco }}</p>
    }
  `,
  styles: [
    `
      .collection-toolbar {
        display: flex;
        justify-content: flex-end;
        padding: 8px 0;
      }
      .collection-error {
        color: #b71c1c;
      }
      .collection-empty {
        padding: 16px;
        opacity: 0.7;
      }
      .collection-actions {
        width: 1%;
        white-space: nowrap;
      }
    `,
  ],
})
export class ContextCollectionComponent {
  private readonly data = inject(ActivatedRoute).snapshot.data;
  private readonly contextName = this.data[CONTEXT_COLLECTION_CONTEXT_KEY] as string;
  private readonly attrName = this.data[CONTEXT_COLLECTION_PATH_KEY] as string;
  private readonly entityName = this.data[CONTEXT_COLLECTION_ENTITY_KEY] as string;

  private readonly service = inject(EmbeddedCollectionService);
  private readonly workingContext = inject(WorkingContextService);
  private readonly descriptorRegistry = inject(BaseEntityDescriptorRegistry);
  private readonly dialog = inject(MatDialog);
  private readonly formNavigator = inject(BaseFormNavigatorSingletonStore);
  private readonly transloco = inject(TranslocoService);
  private readonly activeLang = injectActiveLang();

  protected readonly rows = signal<CollectionItem[]>([]);
  protected readonly isLoading = signal(false);
  protected readonly error = signal<string | undefined>(undefined);

  private readonly context = computed(() => this.workingContext.current(this.contextName));
  private readonly descriptor = computed(() => this.descriptorRegistry.getDescriptor(this.entityName));
  protected readonly columns = computed<BaseEntityAttrDescriptor[]>(() => filterAttributeDescriptors(this.descriptor()?.attrDescriptors ?? []).filter((attr) => attr.hideInTable !== true));
  protected readonly displayedColumns = computed(() => [...this.columns().map((column) => column.attrName), 'actions']);
  protected readonly canChange = computed(() => this.context() !== undefined && this.descriptor() !== undefined);

  /** Reloads whenever another entity becomes the active one — the list follows the context, not the other way round. */
  private readonly reloadOnContextChange = effect(() => {
    const id = this.context()?.id;
    untracked(() => void this.reload(id));
  });

  // region event handling methods
  /** Opens a new row's form below the context; it saves the row on its own and returns here. */
  protected async onAdd(): Promise<void> {
    await this.openForm(BaseUrlSegments.NewEntity);
  }

  /** Opens the row's own form — the screen its entity type has everywhere — below the context, with the owner in the URL. */
  protected async onEdit(row: CollectionItem): Promise<void> {
    await this.openForm(this.rowKey(row));
  }

  protected async onDelete(row: CollectionItem): Promise<void> {
    const data: DeleteConfirmationDialogData = {
      titleKey: 'base_entity.context_collection.delete_title',
      contentKey: 'base_entity.context_collection.delete_content',
      cancelButtonKey: 'base_entity.context_collection.cancel',
      confirmButtonKey: 'base_entity.context_collection.delete',
    };
    const confirmed = await firstValueFrom(this.dialog.open(DeleteConfirmationDialog, { data }).afterClosed());
    if (confirmed === true) await this.change((id) => this.service.remove(this.rootEntityName(), id, this.attrName, String(row['id']), itemVersionOf(row)));
  }
  // endregion

  // region protected, private helper methods
  protected testId(suffix: string): string {
    return createTestId(this.entityName, 'context-collection-' + suffix);
  }

  protected display(column: BaseEntityAttrDescriptor, row: CollectionItem): string {
    const value = row[column.attrName];
    if (value == null) return '';
    if (column.formControlType === FormControlType.DATE) return String(formatDateValue(value, effectiveDateFormat(column), this.activeLang()));
    if (Array.isArray(value)) return value.map(String).join(', ');
    return typeof value === 'object' ? '…' : String(value);
  }

  private rootEntityName(): string {
    return this.context()?.definition.entityName ?? '';
  }

  /**
   * The embedded form of a row: `<context url>/<item type>/<key>/details`, the URL chain the embedded screens read their
   * aggregate from. The navigator keeps the list's URL as where to return to.
   */
  private async openForm(key: string): Promise<void> {
    const contextUrl = this.context()?.url;
    if (!contextUrl || !key) return;
    await this.formNavigator.navigateToUrl(`${contextUrl}/${snakeCaseName(this.entityName)}/${encodeURIComponent(key)}/${BaseUrlSegments.DetailsForm}`);
  }

  /** The key the URL addresses a row by: the attribute the owner's descriptor says identifies it, `id` otherwise. */
  private rowKey(row: CollectionItem): string {
    const owner = this.descriptorRegistry.getDescriptor(this.rootEntityName());
    return rowId(row, owner?.embeddedAttrFor(this.entityName)?.referenceIdField);
  }

  /** Runs one change against the active entity, then shows what is stored now — the server's answer is the truth, also after a conflict. */
  private async change(operation: (id: string) => Promise<unknown>): Promise<void> {
    const id = this.context()?.id;
    if (!id) return;
    this.error.set(undefined);
    try {
      await operation(id);
    } catch (error) {
      this.error.set(this.messageOf(error));
    }
    await this.reload(id);
  }

  private async reload(id: string | undefined): Promise<void> {
    if (!id) {
      this.rows.set([]);
      return;
    }
    this.isLoading.set(true);
    try {
      this.rows.set((await this.service.list(this.rootEntityName(), id, this.attrName)).items);
    } catch (error) {
      this.error.set(this.messageOf(error));
    } finally {
      this.isLoading.set(false);
    }
  }

  private messageOf(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 409) return this.transloco.translate('base_entity.context_collection.conflict');
    return httpErrorMessage(error);
  }
  // endregion
}
