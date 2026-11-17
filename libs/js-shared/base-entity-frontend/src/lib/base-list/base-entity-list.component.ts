import { AfterViewInit, Component, computed, effect, inject, InjectionToken, OnInit, Signal, signal, untracked, ViewChild } from '@angular/core';
import { BaseEntity, PersistedEntity } from '../base-entity/base-entity';
import { MatCell, MatCellDef, MatColumnDef, MatHeaderCell, MatHeaderCellDef, MatHeaderRow, MatHeaderRowDef, MatRow, MatRowDef, MatTable, MatTableDataSource } from '@angular/material/table';
import { MatSort, MatSortHeader } from '@angular/material/sort';
import { MatPaginator } from '@angular/material/paginator';
import { ActivatedRoute, ROUTER_OUTLET_DATA } from '@angular/router';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { listQueryOf, routeListFilterOf, RouteListFilter } from '../base-entity-screens/route-list-filter';
import { WorkingContextService } from '../working-context/working-context.service';
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
import { type NavigationPayload, NavigatorCommand } from '../base-form-navigator/navigation-payload';
import { BaseFormNavigatorSingletonStore } from '../base-form-navigator/base-form-navigator.store';
import { BaseEntityStoreApi } from '../base-entity-store/base-entity.store';
import { EntityLabelPipe } from '../i18n/entity-label.pipe';
import { DateFormatPipe } from '../i18n/date-format.pipe';
import { ForeignKeyLabels } from '../base-form/foreign-key/foreign-key-labels';
import { commentsOf } from '../base-form/comments/entity-comment';
import { TranslocoPipe } from '@jsverse/transloco';
import { AddressPipe } from '../base-form/address/address.pipe';
import { GeoLocationPipe } from '../base-form/geo-location/geo-location.pipe';
import { WorkingContextDefinition } from '../working-context/working-context.definition';


export const BASE_LIST_DESCRIPTORS = new InjectionToken<string[]>('BASE_TABLE_DISPLAYED_COLUMNS');

@Component({
  selector: 'base-list',
  standalone: true,
  imports: [
    MatSlideToggle,
    TranslocoPipe,
    AddressPipe,
    GeoLocationPipe,
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
    EntityLabelPipe,
    DateFormatPipe,
  ],
  templateUrl: 'base-entity-list.component.html',
  styleUrl: 'base-entity-list.component.css',
})
export class BaseEntityListComponent<Entity extends BaseEntity> implements AfterViewInit, OnInit {
  dataSource: MatTableDataSource<PersistedEntity<Entity>> = new MatTableDataSource<PersistedEntity<Entity>>();
  selection = new SelectionModel<PersistedEntity<Entity>>(true, []);
  protected readonly entityDescriptor = inject(ROUTER_OUTLET_DATA) as Signal<BaseEntityDescriptor>;
  private readonly logger = inject(NGXLogger);
  private readonly formNavigator = inject(BaseFormNavigatorSingletonStore);
  private readonly objectStoreService = inject(ObjectStoreService);
  private readonly foreignKeyLabels = inject(ForeignKeyLabels);
  private readonly route = inject(ActivatedRoute);
  /** The filters this list's route declares: its RSQL, and the working context it may be narrowed to. */
  protected readonly routeFilter: RouteListFilter = routeListFilterOf(this.route.snapshot, this.entityDescriptor().entityName);
  /** The active entity of the context the list may be narrowed to; `undefined` when none is active. */
  protected readonly contextEntityId = computed(() => (this.routeFilter.filterByContext ? this.workingContext.current(this.routeFilter.filterByContext)?.id : undefined));
  /** Narrowing to the active entity: on by default, as the spec's optional view offers it. */
  protected readonly onlyActiveContext = signal(true);
  /** Whether the toggle is shown: the list may be narrowed, and there is something to narrow it to. */
  protected readonly showsContextToggle = computed(() => this.routeFilter.filterByContext !== undefined && this.contextEntityId() !== undefined);
  /** Set once the first load is made, so that a context change before it does not load twice. */
  private viewReady = false;
  private readonly workingContext = inject(WorkingContextService);
  /** The working contexts this entity is the root of. They add a "Set as context object" button below the list, enabled while exactly one row is selected. */
  protected readonly workingContexts: Signal<WorkingContextDefinition[]> = computed(() => this.workingContext.definitionsForEntity(this.entityDescriptor().entityName));
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
  store: BaseEntityStoreApi<PersistedEntity<Entity>>;
  protected selectOrCreateMode = signal(false);
  private requestPayload?: NavigationPayload;

  /** The URL a user was sent away from for lack of a context object, which choosing one continues to; `undefined` otherwise. */
  protected readonly pendingTarget = computed(() => {
    const definition = this.workingContexts()[0];
    return definition?.aggregate ? this.workingContext.pendingTarget(definition.name) : undefined;
  });

  constructor() {
    // An entity that canContextObject is the root of its own context, with no declaration of it anywhere.
    if (this.entityDescriptor().canContextObject) this.workingContext.ensureAggregate(this.entityDescriptor().entityName);
    this.store = this.entityDescriptor().store as BaseEntityStoreApi<PersistedEntity<Entity>>;
    this.registerEffects();
  }

  // region Angular lifecycle events
  ngAfterViewInit(): void {
    this.dataSource = this.store?.matTableDataSource();
    this.dataSource.sort = this.sort;
    this.dataSource.paginator = this.paginator;
    this.loadAllData();
    this.viewReady = true;
  }

  ngOnInit(): void {
    this.formNavigator.setEntityName(this.entityDescriptor().entityName);
    this.formNavigator.determineActiveRouteSegment();
    this.requestPayload = this.formNavigator.popRequestPayload();
    this.selectOrCreateMode.set(this.requestPayload?.command === NavigatorCommand.SELECT_OR_CREATE);
    this.logger.info('BaseEntityListComponent initialized with:', { columnDescriptors: this.columnDescriptors() });
  }

  // endregion

  // region event handling methods
  onChangeSelection(entity?: PersistedEntity<Entity>) {
    if (entity && this.isSelected(entity)) this.store.selectEntity(this.keyOf(entity));
    else if (entity) this.store.deselectEntity(this.keyOf(entity));

    if (this.selection.selected.length === 1) this.store.setCurrentEntity(this.keyOf(this.selection.selected[0]));
    else this.store.clearCurrentEntity();
  }

  onNavigateToDetails(entity: PersistedEntity<Entity>): void {
    this.formNavigator.navigateToDetails(this.entityDescriptor().entityName, this.keyOf(entity));
    this.store.setCurrentEntity(this.keyOf(entity));
  }

  onNavigateToRelated(config: BaseEntityAttrDescriptor, entity: PersistedEntity<Entity>) {
    const relatedEntityName = config.linkedEntityType;
    if (!relatedEntityName) return;

    this.formNavigator.navigateToRelated(relatedEntityName, this.getPropertyValue(entity, config.attrName) as string);
  }

  onRowClick(entity: PersistedEntity<Entity>) {
    this.selection.toggle(entity);
    this.onChangeSelection(entity);
  }

  /** Enter on a row of a context entity makes it the active one, as double-click does; elsewhere it selects. */
  onRowEnter(entity: PersistedEntity<Entity>) {
    if (this.workingContexts().length > 0) void this.onSetActive(this.workingContexts()[0], entity);
    else this.onRowClick(entity);
  }

  onRowDoubleClick(entity: PersistedEntity<Entity>) {
    if (this.workingContexts().length > 0) void this.onSetActive(this.workingContexts()[0], entity);
  }

  async onSetActive(definition: WorkingContextDefinition, entity: PersistedEntity<Entity>): Promise<void> {
    await this.workingContext.select(definition.name, entity.id);
  }

  /** Makes the one selected row the context object. */
  async onSetSelectedAsContext(): Promise<void> {
    const [entity] = this.selection.selected;
    if (this.canSetContextObject() && entity) await this.onSetActive(this.workingContexts()[0], entity);
  }

  canSetContextObject(): boolean {
    return this.workingContexts().length > 0 && this.selection.selected.length === 1;
  }

  /** A FOREIGN_KEY cell shows the linked object's identifying attribute, as the form does, not its id. */
  foreignKeyLabel(config: BaseEntityAttrDescriptor, entity: PersistedEntity<Entity>): string {
    return this.foreignKeyLabels.labelOf(config.linkedEntityType, this.getPropertyValue(entity, config.attrName));
  }

  /** An ARTIFACT cell's value as a list: a multi-valued attribute holds an array, a single-valued one an object. */
  artifactsOf(value: unknown): ArtifactAttr[] {
    if (value == null) return [];
    return (Array.isArray(value) ? value : [value]).filter((item): item is ArtifactAttr => typeof item === 'object' && item !== null);
  }

  /** A COMMENTS cell shows how many comments the record has, not the comments. */
  commentCount(value: unknown): number {
    return commentsOf(value).length;
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
        this.store.selectEntity(this.keyOf(entity));
      });
    }
  }

  async onCancelSelectOrCreate(): Promise<void> {
    this.cancelSelections();
    await this.formNavigator.navigateBack();
  }

  async onSelectEntity(): Promise<void> {
    if (!this.canSelectEntity()) return;

    this.formNavigator.pushResponsePayload({
      command: NavigatorCommand.SELECT_OR_CREATE,
      attrName: this.requestPayload?.attrName,
      payload: this.selection.selected[0],
      context: this.requestPayload?.context,
    });
    await this.formNavigator.navigateBack();
  }

  // endregion

  // region protected, private helper methods
  /** The row's key as its store knows it: an embedded row is keyed by its `referenceIdField`, which is not always `id`. */
  private keyOf(entity: PersistedEntity<Entity>): string {
    return this.store.keyOf?.(entity) ?? entity.id;
  }

  private cancelSelections() {
    this.store.deselectAll();
    this.selection.clear();
  }

  private doFilter(filterKey: string) {
    this.dataSource.filter = filterKey.trim().toLocaleLowerCase();
  }

  private getPropertyValue(entity: PersistedEntity<Entity>, property: string): unknown {
    return Reflect.get(entity, property);
  }

  /** Narrows the list to the active entity, or shows every row of the entity again. */
  protected onToggleContextFilter(on: boolean): void {
    this.onlyActiveContext.set(on);
  }

  private loadAllData() {
    const pathParams = new Map<string, string>([]);
    const filters: FilterCondition[] = [];
    this.store.load({ pathParams, filters, query: this.rsqlQuery() });
  }

  private rsqlQuery(): string | undefined {
    return listQueryOf(this.routeFilter, this.contextEntityId(), this.onlyActiveContext());
  }

  protected registerEffects(): void {
    effect(() => (this.dataSource.data = this.store.entities()));
    effect(() => this.doFilter(this.store.filterKey() ?? ''));
    // Reloads when the active entity changes or the toggle is flipped: the list follows the context.
    effect(() => {
      this.rsqlQuery();
      untracked(() => {
        if (this.viewReady) this.loadAllData();
      });
    });
  }

  // endregion

  // region properties
  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource.data.length;
    return numSelected == numRows;
  }

  isSelected(row: PersistedEntity<Entity>) {
    return this.selection.isSelected(row);
  }

  canSelectEntity(): boolean {
    return this.selection.selected.length === 1;
  }

  // endregion
}
