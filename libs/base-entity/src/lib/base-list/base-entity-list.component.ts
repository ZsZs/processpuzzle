import { AfterViewInit, Component, computed, effect, inject, InjectionToken, OnInit, Signal, signal, ViewChild } from '@angular/core';
import { BaseEntity } from '../base-entity/base-entity';
import { MatCell, MatCellDef, MatColumnDef, MatHeaderCell, MatHeaderCellDef, MatHeaderRow, MatHeaderRowDef, MatRow, MatRowDef, MatTable, MatTableDataSource } from '@angular/material/table';
import { MatSort, MatSortHeader } from '@angular/material/sort';
import { MatPaginator } from '@angular/material/paginator';
import { ROUTER_OUTLET_DATA } from '@angular/router';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { MatProgressBar } from '@angular/material/progress-bar';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { MatCheckbox } from '@angular/material/checkbox';
import { SelectionModel } from '@angular/cdk/collections';
import { filterAttributeDescriptors } from '../base-entity/filter-attr-descriptor';
import { FilterCondition } from '../base-entity-service/base-entity-load-response';
import { NGXLogger } from 'ngx-logging-kit';
import { ObjectStoreService } from '../object-store/object-store.service';
import { SlicePipe } from '@angular/common';
import { ArtifactAttr } from '../base-form/artifact/artifact-attr';
import { MatButton } from '@angular/material/button';
import { NavigatorCommand } from '../base-form-navigator/navigation-payload';
import { BaseFormNavigatorSingletonStore } from '../base-form-navigator/base-form-navigator.store';

export const BASE_LIST_DESCRIPTORS = new InjectionToken<string[]>('BASE_TABLE_DISPLAYED_COLUMNS');

@Component({
  selector: 'base-list',
  standalone: true,
  imports: [
    MatTable,
    MatSort,
    MatHeaderCell,
    MatPaginator,
    MatHeaderRow,
    MatRow,
    MatCell,
    MatColumnDef,
    MatHeaderCellDef,
    MatCellDef,
    MatHeaderRowDef,
    MatRowDef,
    MatSortHeader,
    MatProgressBar,
    MatCheckbox,
    SlicePipe,
    MatButton,
  ],
  templateUrl: 'base-entity-list.component.html',
  styleUrl: 'base-entity-list.component.css',
})
export class BaseEntityListComponent<Entity extends BaseEntity> implements AfterViewInit, OnInit {
  dataSource: MatTableDataSource<Entity> = new MatTableDataSource<Entity>();
  selection = new SelectionModel<Entity>(true, []);
  protected readonly entityDescriptor = inject(ROUTER_OUTLET_DATA) as Signal<BaseEntityDescriptor>;
  private readonly logger = inject(NGXLogger);
  private readonly formNavigator = inject(BaseFormNavigatorSingletonStore);
  private readonly objectStoreService = inject(ObjectStoreService);
  columnDescriptors: Signal<BaseEntityAttrDescriptor[]> = computed(() => {
    return filterAttributeDescriptors(this.entityDescriptor().attrDescriptors);
  });
  displayedColumns: Signal<string[]> = computed(() => {
    const columns = this.columnDescriptors()
      .filter((column) => column.hideInTable !== true)
      .map((column) => column.attrName);
    columns.unshift('select');
    return columns;
  });
  @ViewChild(MatSort) sort = {} as MatSort;
  @ViewChild(MatPaginator) paginator = {} as MatPaginator;
  store: any;
  protected selectOrCreateMode = signal(false);

  constructor() {
    this.store = this.entityDescriptor().store;
    this.registerEffects();
  }

  // region Angular lifecycle events
  ngAfterViewInit(): void {
    this.dataSource = this.store?.matTableDataSource();
    this.dataSource.sort = this.sort;
    this.dataSource.paginator = this.paginator;
    this.loadAllData();
  }

  ngOnInit(): void {
    this.formNavigator.setEntityName(this.entityDescriptor().entityName);
    this.formNavigator.determineActiveRouteSegment();
    const requestPayload = this.formNavigator.popRequestPayload();
    this.selectOrCreateMode.set(requestPayload?.command === NavigatorCommand.SELECT_OR_CREATE);
    // eslint-disable-next-line no-console
    console.debug('[BaseEntityListComponent] ngOnInit', { entityName: this.entityDescriptor().entityName, requestPayload, selectOrCreateMode: this.selectOrCreateMode() });
    this.logger.info('BaseEntityListComponent initialized with:', { columnDescriptors: this.columnDescriptors() });
  }

  // endregion

  // region event handling methods
  onChangeSelection(entity?: Entity) {
    if (entity && this.isSelected(entity)) this.store.selectEntity(entity.id);
    else if (entity) this.store.deselectEntity(entity.id);

    if (this.selection.selected.length === 1) this.store.setCurrentEntity(this.selection.selected[0].id);
    else this.store.clearCurrentEntity();
  }

  onNavigateToDetails(entity: Entity): void {
    this.formNavigator.navigateToDetails(this.entityDescriptor().entityName, entity.id);
    this.store.setCurrentEntity(entity.id);
  }

  onNavigateToRelated(config: BaseEntityAttrDescriptor, entity: Entity) {
    const relatedEntityName = config.linkedEntityType?.entityName;
    if (!relatedEntityName) return;

    this.formNavigator.navigateToRelated(relatedEntityName, this.getPropertyValue(entity, config.attrName));
  }

  onRowClick(entity: Entity) {
    this.selection.toggle(entity);
    this.onChangeSelection(entity);
  }

  onDownloadObject(artifact: ArtifactAttr, event?: MouseEvent): void {
    event?.stopPropagation();
    event?.preventDefault();

    const bucketName = artifact.bucket ?? artifact.bucket;
    const objectID = artifact.objectId ?? artifact.objectId;
    if (!bucketName || !objectID) {
      this.logger.warn('Cannot download artifact: missing bucketName or objectID', { artifact });
      return;
    }

    this.objectStoreService.getObjectByID(bucketName, objectID).subscribe({
      next: (response) => {
        const blobUrl = URL.createObjectURL(response.data);
        window.open(blobUrl, '_blank', 'noopener');
        setTimeout(() => URL.revokeObjectURL(blobUrl), 60000);
      },
      error: (error) => {
        this.logger.error('Artifact download failed', { bucketName, objectID, error });
      },
    });
  }

  onToggleAllRows(): void {
    if (this.isAllSelected()) {
      this.cancelSelections();
    } else {
      this.dataSource.data.forEach((entity) => {
        this.selection.select(entity);
        this.store.selectEntity(entity.id);
      });
    }
  }

  async onCancelSelectOrCreate(): Promise<void> {
    this.cancelSelections();
    await this.formNavigator.navigateBack();
  }

  async onSelectEntity(): Promise<void> {
    if (!this.canSelectEntity()) return;

    this.formNavigator.pushResponsePayload({ command: NavigatorCommand.SELECT_OR_CREATE, payload: this.selection.selected[0] });
    await this.formNavigator.navigateBack();
  }

  // endregion

  // region protected, private helper methods
  private cancelSelections() {
    this.store.deselectAll();
    this.selection.clear();
  }

  private doFilter(filterKey: string) {
    this.dataSource.filter = filterKey.trim().toLocaleLowerCase();
  }

  private getPropertyValue(entity: Entity, property: string): any {
    return Reflect.get(entity, property);
  }

  private loadAllData() {
    const pathParams = new Map<string, string>([]);
    const filters: FilterCondition[] = [];
    this.store.load({ pathParams, filters });
  }

  protected registerEffects(): void {
    effect(() => (this.dataSource.data = this.store.entities()));
    effect(() => this.doFilter(this.store.filterKey() ?? ''));
  }

  // endregion

  // region properties
  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource.data.length;
    return numSelected == numRows;
  }

  isSelected(row: Entity) {
    return this.selection.isSelected(row);
  }

  canSelectEntity(): boolean {
    return this.selection.selected.length === 1;
  }

  // endregion
}
