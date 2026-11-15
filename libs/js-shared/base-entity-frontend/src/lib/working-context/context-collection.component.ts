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
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { effectiveDateFormat, formatDateValue } from '../base-entity/date-format';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { filterAttributeDescriptors } from '../base-entity/filter-attr-descriptor';
import { BaseEntityDescriptorRegistry } from '../base-entity-facade/base-entity-descriptor.registry';
import { DeleteConfirmationDialog, DeleteConfirmationDialogData } from '../dialogs/delete-confirmation.dialog';
import { EntityLabelPipe } from '../i18n/entity-label.pipe';
import { ContextCollectionItemDialog, ContextCollectionItemDialogData } from './context-collection-item.dialog';
import { CollectionItem, EmbeddedCollectionService, mergePatch } from './embedded-collection.service';
import { WorkingContextService } from './working-context.service';

/** Route data: the context the collection belongs to. */
export const CONTEXT_COLLECTION_CONTEXT_KEY = 'collectionContext';
/** Route data: the attribute of the context's entity that carries the collection. */
export const CONTEXT_COLLECTION_PATH_KEY = 'collectionPath';
/** Route data: the embedded entity type of the collection's items. */
export const CONTEXT_COLLECTION_ENTITY_KEY = 'collectionEntity';

/**
 * A list of the embedded collection of the **active** entity of a working context — the participants of the active
 * race — with add, edit and remove acting on that entity alone: the user never selects a race here, and a change sends
 * the item rather than the race (see {@link EmbeddedCollectionService}).
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
  protected async onAdd(): Promise<void> {
    const item = await this.askForItem();
    if (item) await this.change((id) => this.service.append(this.rootEntityName(), id, this.attrName, item));
  }

  protected async onEdit(row: CollectionItem): Promise<void> {
    const edited = await this.askForItem(row);
    if (!edited) return;
    const patch = mergePatch(row, edited);
    if (Object.keys(patch).length === 0) return;
    await this.change((id) => this.service.patch(this.rootEntityName(), id, this.attrName, String(row['id']), patch));
  }

  protected async onDelete(row: CollectionItem): Promise<void> {
    const data: DeleteConfirmationDialogData = {
      titleKey: 'base_entity.context_collection.delete_title',
      contentKey: 'base_entity.context_collection.delete_content',
      cancelButtonKey: 'base_entity.context_collection.cancel',
      confirmButtonKey: 'base_entity.context_collection.delete',
    };
    const confirmed = await firstValueFrom(this.dialog.open(DeleteConfirmationDialog, { data }).afterClosed());
    if (confirmed === true) await this.change((id) => this.service.remove(this.rootEntityName(), id, this.attrName, String(row['id'])));
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

  private async askForItem(item?: CollectionItem): Promise<CollectionItem | undefined> {
    const descriptor = this.descriptor();
    if (!descriptor) return undefined;
    const data: ContextCollectionItemDialogData = { descriptor, item, store: this.descriptorRegistry.getStore(this.entityName) };
    return (await firstValueFrom(this.dialog.open(ContextCollectionItemDialog, { data, width: '640px', maxWidth: '100vw' }).afterClosed())) as CollectionItem | undefined;
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
