import { Component, computed, inject, input, OnInit, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatToolbar } from '@angular/material/toolbar';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { injectActiveLang } from '@processpuzzle/util';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { effectiveDateFormat, formatDateValue } from '../base-entity/date-format';
import { filterAttributeDescriptors } from '../base-entity/filter-attr-descriptor';
import { BaseEntity } from '../base-entity/base-entity';
import { createTestId } from '../base-entity/base-entity-utility';
import { rowId } from '../base-entity-embedded/embedded-aggregate';
import { EmbeddedBreadcrumbLevel } from '../base-entity-embedded/embedded-route-context';
import { BaseEntityDescriptorRegistry } from '../base-entity-facade/base-entity-descriptor.registry';
import { BaseEntityStoreApi } from '../base-entity-store/base-entity.store';
import { BaseFormNavigatorSingletonStore } from '../base-form-navigator/base-form-navigator.store';
import { BaseUrlSegments } from '../base-form-navigator/base-url-segments';
import { translateLabel } from '../i18n/entity-label.pipe';
import { UnsavedChangesRegistry } from '../base-form/unsaved-changes/unsaved-changes.registry';
import { RuleViolationsSingletonStore } from '../rule-engine/rule-violations.store';
import { EntityRouteRegistry } from '../base-form-navigator/entity-route.registry';
import { ActiveWorkingContext, WorkingContextService } from '../working-context/working-context.service';
import { contextLabel, renderTemplate, valueAt } from '../working-context/working-context-attributes';
import { WORKING_CONTEXT_DRAWER_CONFIG, WorkingContextPickerDialog, WorkingContextPickerDialogData } from '../working-context/working-context-picker.dialog';

/** One step of the hierarchy as it is rendered: what to show, and what it is. */
interface EntityCrumb {
  entityName: string;
  /** The crumb's text — the row's identifying value, or what stands in for it. */
  title: string;
  /** The entity's translated name, shown as the crumb's tooltip. */
  label: string;
  testId: string;
}

/** An active working context as the status bar renders it: the root of the breadcrumb. */
interface ContextCrumb {
  context: ActiveWorkingContext;
  title: string;
  /** The entity's translated name, shown as the crumb's tooltip. */
  label: string;
  badge: string | undefined;
  extra: string | undefined;
  testId: string;
}

/**
 * Says which record is open, and — when it was reached through the entities that contain it — how it was
 * reached.
 *
 * Embedded components nest arbitrarily deep (`Test Entity` → `Embedded Component` → `Embedded Detail`), and
 * each level's screen replaces the previous one rather than nesting inside it. The breadcrumb is what makes
 * that legible: it is the one place the hierarchy is shown, and the way back up. It is read from the URL
 * (`BaseFormNavigatorSingletonStore.breadcrumb`), so it describes a deep link or a refresh exactly as it
 * describes a drill-down.
 */
@Component({
  selector: 'base-entity-statusbar',
  standalone: true,
  imports: [CommonModule, MatToolbar, MatIcon, MatMenu, MatMenuItem, MatMenuTrigger, TranslocoPipe],
  template: `
    <mat-toolbar *ngIf="isVisible()">
      <nav class="breadcrumb" [attr.aria-label]="'base_entity.statusbar.breadcrumb_label' | transloco">
        @for (crumb of contextCrumbs(); track crumb.context.name) {
          <span class="context-crumb" [attr.data-testid]="crumb.testId">
            <button type="button" class="crumb crumb-context" [attr.data-testid]="crumb.testId + '-switch'" [title]="crumb.label" (click)="onSwitchContext(crumb)">
              {{ crumb.title }}
              @if (crumb.extra) {
                <span class="context-extra">{{ crumb.extra }}</span>
              }
            </button>
            @if (crumb.badge) {
              <span class="chip context-badge" [attr.data-testid]="crumb.testId + '-badge'">{{ crumb.badge }}</span>
            }
            <button
              type="button"
              class="context-menu-trigger"
              [attr.data-testid]="crumb.testId + '-menu'"
              [attr.aria-label]="'base_entity.working_context.menu' | transloco"
              [matMenuTriggerFor]="contextMenu"
            >
              <mat-icon>arrow_drop_down</mat-icon>
            </button>
            <mat-menu #contextMenu="matMenu">
              <button mat-menu-item [attr.data-testid]="crumb.testId + '-menu-switch'" (click)="onSwitchContext(crumb)">
                <mat-icon>swap_horiz</mat-icon><span>{{ 'base_entity.working_context.switch' | transloco }}</span>
              </button>
              <button mat-menu-item [attr.data-testid]="crumb.testId + '-menu-open'" (click)="onOpenContext(crumb)">
                <mat-icon>edit</mat-icon><span>{{ 'base_entity.working_context.open' | transloco }}</span>
              </button>
              <button mat-menu-item [attr.data-testid]="crumb.testId + '-menu-clear'" (click)="onClearContext(crumb)">
                <mat-icon>close</mat-icon><span>{{ 'base_entity.working_context.clear' | transloco }}</span>
              </button>
            </mat-menu>
          </span>
          @if (crumbs().length > 0) {
            <mat-icon class="crumb-separator">chevron_right</mat-icon>
          }
        }
        @for (crumb of crumbs(); track $index; let index = $index; let isLast = $last) {
          @if (isLast) {
            <span class="crumb crumb-current" [attr.data-testid]="crumb.testId" [title]="crumb.label">{{ crumb.title }}</span>
          } @else {
            <button type="button" class="crumb crumb-link" [attr.data-testid]="crumb.testId" [title]="crumb.label" (click)="onNavigateToCrumb(index)">{{ crumb.title }}</button>
            <mat-icon class="crumb-separator">chevron_right</mat-icon>
          }
        }
      </nav>
      <span class="statusbar-spacer"></span>
      @if (hasUnsavedChanges()) {
        <span class="chip unsaved-changes" data-testid="unsaved-changes-indicator" role="status" [title]="'base_entity.statusbar.unsaved_changes_hint' | transloco: { entity: crumbs()[0].label }">
          <mat-icon>edit_note</mat-icon>
          <span>{{ 'base_entity.statusbar.unsaved_changes' | transloco }}</span>
        </span>
      }
      <div class="rule-summary" *ngIf="showViolationSummary()">
        <span class="chip severity-error" *ngIf="errorCount() > 0">
          <mat-icon>error</mat-icon>
          <span>{{ errorCount() }}</span>
        </span>
        <span class="chip severity-warning" *ngIf="warningCount() > 0">
          <mat-icon>warning</mat-icon>
          <span>{{ warningCount() }}</span>
        </span>
        <span class="chip severity-info" *ngIf="infoCount() > 0">
          <mat-icon>info</mat-icon>
          <span>{{ infoCount() }}</span>
        </span>
      </div>
    </mat-toolbar>
  `,
  styles: [
    `
      mat-toolbar {
        background-color: #ffffff;
        border-radius: 6px;
        margin-bottom: 3px;
      }
      .statusbar-spacer {
        flex: 1 1 auto;
      }
      .breadcrumb {
        display: flex;
        align-items: center;
        min-width: 0;
        gap: 2px;
      }
      .crumb {
        overflow: hidden;
        max-width: 24ch;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .crumb-link {
        padding: 0 2px;
        border: none;
        background: none;
        color: inherit;
        font: inherit;
        cursor: pointer;
        text-decoration: underline;
      }
      .context-crumb {
        display: inline-flex;
        align-items: center;
        gap: 4px;
      }
      .crumb-context,
      .context-menu-trigger {
        min-height: 48px;
        border: none;
        background: none;
        color: inherit;
        font: inherit;
        font-weight: 500;
        cursor: pointer;
      }
      .context-menu-trigger {
        display: inline-flex;
        align-items: center;
        padding: 0;
      }
      .context-extra {
        margin-left: 6px;
        font-weight: 400;
        opacity: 0.7;
      }
      .chip.context-badge {
        background-color: rgba(0, 0, 0, 0.08);
      }
      .crumb-current {
        font-weight: 500;
      }
      .crumb-separator {
        width: 18px;
        height: 18px;
        font-size: 18px;
        line-height: 18px;
        opacity: 0.6;
      }
      .rule-summary {
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .chip {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        padding: 2px 8px;
        border-radius: 12px;
        font-size: 13px;
        line-height: 1;
      }
      .chip mat-icon {
        width: 16px;
        height: 16px;
        font-size: 16px;
        line-height: 16px;
      }
      .chip.severity-error {
        background-color: rgba(211, 47, 47, 0.15);
        color: #b71c1c;
      }
      .chip.severity-warning {
        background-color: rgba(245, 124, 0, 0.15);
        color: #b26a00;
      }
      .chip.unsaved-changes {
        margin-right: 8px;
        background-color: rgba(245, 124, 0, 0.15);
        color: #b26a00;
      }
      .chip.severity-info {
        background-color: rgba(25, 118, 210, 0.15);
        color: #0d47a1;
      }
    `,
  ],
})
export class BaseEntityStatusbarComponent implements OnInit {
  store!: BaseEntityStoreApi<BaseEntity>;
  entityDescriptor = input.required<BaseEntityDescriptor>();
  entityTitle: Signal<string> = computed<string>(() => this.resolveTitle());
  crumbs: Signal<EntityCrumb[]> = computed<EntityCrumb[]>(() => this.resolveCrumbs());
  // More than one crumb means an embedded form is open, which is a record by itself — the own store's
  // selection, the only thing to go by otherwise, belongs to the outermost entity.
  contextCrumbs: Signal<ContextCrumb[]> = computed<ContextCrumb[]>(() => this.workingContext.active().map((context) => this.contextCrumb(context)));
  isVisible: Signal<boolean> = computed(
    () => this.contextCrumbs().length > 0 || this.crumbs().length > 1 || (this.store != null && (this.store.currentEntity() !== undefined || this.store.selectedEntities().length === 1)),
  );

  private readonly descriptorRegistry = inject(BaseEntityDescriptorRegistry);
  private readonly workingContext = inject(WorkingContextService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly entityRouteRegistry = inject(EntityRouteRegistry);
  private readonly formNavigator = inject(BaseFormNavigatorSingletonStore);
  private readonly transloco = inject(TranslocoService);
  private readonly activeLang = injectActiveLang();
  private readonly violationsStore = inject(RuleViolationsSingletonStore);
  readonly hasUnsavedChanges = inject(UnsavedChangesRegistry).hasUnsavedChanges;
  errorCount = this.violationsStore.errorCount;
  warningCount = this.violationsStore.warningCount;
  infoCount = this.violationsStore.infoCount;
  /** The violations of the form the user is actually on, which at depth is the deepest crumb's. */
  showViolationSummary: Signal<boolean> = computed(
    () => this.violationsStore.entityName() === (this.crumbs().at(-1)?.entityName ?? this.entityDescriptor().entityName) && this.violationsStore.hasViolations(),
  );

  // region Angular lifecycle hooks
  ngOnInit(): void {
    this.store = this.entityDescriptor().store as BaseEntityStoreApi<BaseEntity>;
  }

  // endregion

  // region event handling methods
  async onNavigateToCrumb(index: number): Promise<void> {
    await this.formNavigator.navigateToBreadcrumbLevel(index);
  }

  /** Opens the context's picker as a drawer; picking in it switches, and closes it. */
  onSwitchContext(crumb: ContextCrumb): void {
    const data: WorkingContextPickerDialogData = { contextName: crumb.context.name, entityLabel: crumb.label };
    this.dialog.open(WorkingContextPickerDialog, { ...WORKING_CONTEXT_DRAWER_CONFIG, data });
  }

  /** The context's entity in its own form, for editing it — the context stays active. */
  async onOpenContext(crumb: ContextCrumb): Promise<void> {
    const url = this.entityRouteRegistry.detailsPath(crumb.context.definition.entityName, crumb.context.id);
    if (url) await this.router.navigateByUrl(url);
  }

  async onClearContext(crumb: ContextCrumb): Promise<void> {
    await this.workingContext.clear(crumb.context.name);
  }

  // endregion

  // region protected, private helper methods
  /**
   * One crumb per level the URL walks through. A route that names no entity — nothing to walk — leaves the
   * status bar with the single crumb it has always shown, described by its own descriptor.
   */
  private resolveCrumbs(): EntityCrumb[] {
    const own = this.entityDescriptor();
    const levels = this.formNavigator.breadcrumb();
    if (levels.length === 0) return [this.crumb(own.entityName, this.resolveTitle(), own)];

    let ownerDescriptor: BaseEntityDescriptor | undefined;
    return levels.map((level) => {
      const descriptor = this.descriptorOf(level.entityName);
      const crumb = this.crumb(level.entityName, this.resolveLevelTitle(level, descriptor, ownerDescriptor), descriptor);
      ownerDescriptor = descriptor;
      return crumb;
    });
  }

  /** The own descriptor wins for its own entity: it is the instance this status bar was configured with. */
  private descriptorOf(entityName: string): BaseEntityDescriptor | undefined {
    const own = this.entityDescriptor();
    return own.entityName === entityName ? own : this.descriptorRegistry.getDescriptor(entityName);
  }

  private contextCrumb(context: ActiveWorkingContext): ContextCrumb {
    const { definition, descriptor, entity, id } = context;
    const badgeValue = definition.display?.badge ? valueAt(entity, definition.display.badge, descriptor) : undefined;
    const extra = definition.display?.extra && entity ? renderTemplate(definition.display.extra, entity, descriptor) : undefined;
    return {
      context,
      title: contextLabel(definition.display?.label, entity, descriptor, id),
      label: descriptor ? translateLabel(this.transloco, descriptor.i18nKey(), descriptor.entityName) : definition.entityName,
      badge: badgeValue == null || badgeValue === '' ? undefined : String(badgeValue),
      extra: extra && extra.length > 0 ? extra : undefined,
      testId: createTestId(definition.entityName, 'context-crumb'),
    };
  }

  private crumb(entityName: string, title: string, descriptor: BaseEntityDescriptor | undefined): EntityCrumb {
    return {
      entityName,
      title,
      label: descriptor ? translateLabel(this.transloco, descriptor.i18nKey(), descriptor.entityName) : entityName,
      testId: createTestId(entityName, 'breadcrumb'),
    };
  }

  /**
   * Explicit {@link BaseEntityDescriptor.entityTitle} wins when set; otherwise the level's row is looked up
   * in the entity's own store and labelled by its identifying attribute
   * ({@link BaseEntityDescriptor.titleAttrName}). A row that cannot be found — one still being created, or a
   * level whose store has not loaded — is named by its key, and failing that by its entity.
   */
  private resolveLevelTitle(level: EmbeddedBreadcrumbLevel, descriptor: BaseEntityDescriptor | undefined, ownerDescriptor: BaseEntityDescriptor | undefined): string {
    if (!descriptor) return level.entityId ?? level.entityName;

    const override = this.evaluateEntityTitle(descriptor.entityTitle);
    if (override) return override;

    const attrName = descriptor.titleAttrName();
    const row = this.findRowOfLevel(level, descriptor, ownerDescriptor);
    const value = attrName ? row?.[attrName] : undefined;
    const title = value != null ? this.titleText(descriptor, attrName, value) : '';
    if (title.length > 0) return title;

    return level.entityId && level.entityId !== BaseUrlSegments.NewEntity ? level.entityId : translateLabel(this.transloco, descriptor.i18nKey(), descriptor.entityName);
  }

  /**
   * The row a level stands for, read from that entity's store.
   *
   * `loadById` is deliberately not used: it records a selection, and this runs inside a computed. An
   * embedded row may be keyed by an attribute other than `id` — the owner's attribute descriptor is what
   * declares which ({@link rowId}).
   */
  private findRowOfLevel(level: EmbeddedBreadcrumbLevel, descriptor: BaseEntityDescriptor, ownerDescriptor: BaseEntityDescriptor | undefined): Record<string, unknown> | undefined {
    const store = descriptor.store as BaseEntityStoreApi<BaseEntity> | undefined;
    const currentEntity = store?.currentEntity() as Record<string, unknown> | undefined;
    if (level.entityId === undefined) return currentEntity;

    const referenceIdField = ownerDescriptor?.embeddedAttrFor(level.entityName)?.referenceIdField;
    const rows = (store?.entities() ?? []) as Record<string, unknown>[];
    return rows.find((row) => rowId(row, referenceIdField) === level.entityId) ?? currentEntity;
  }

  /**
   * Explicit {@link BaseEntityDescriptor.entityTitle} wins when set; otherwise the title is the value of
   * the current entity's identifying attribute ({@link BaseEntityDescriptor.titleAttrName}).
   */
  private resolveTitle(): string {
    const descriptor = this.entityDescriptor();
    const override = this.evaluateEntityTitle(descriptor.entityTitle);
    if (override) return override;

    const attrName = descriptor.titleAttrName();
    const currentEntity = this.store?.currentEntity() as Record<string, unknown> | undefined;
    const value = attrName ? currentEntity?.[attrName] : undefined;
    return value != null ? this.titleText(descriptor, attrName, value) : '';
  }

  /** A date title reads in its attribute's style and the active language, as it does in the list. */
  private titleText(descriptor: BaseEntityDescriptor, attrName: string, value: unknown): string {
    const attr = filterAttributeDescriptors(descriptor.attrDescriptors).find((candidate) => candidate.attrName === attrName);
    if (attr?.formControlType !== FormControlType.DATE) return String(value);
    return String(formatDateValue(value, effectiveDateFormat(attr), this.activeLang()));
  }

  private evaluateEntityTitle(title: string | (() => string)): string {
    return typeof title === 'function' ? title() : title;
  }

  // endregion
}
