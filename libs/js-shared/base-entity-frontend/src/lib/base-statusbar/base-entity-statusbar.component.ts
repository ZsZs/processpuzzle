import { Component, computed, inject, input, OnInit, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatToolbar } from '@angular/material/toolbar';
import { MatIcon } from '@angular/material/icon';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseEntity } from '../base-entity/base-entity';
import { createTestId } from '../base-entity/base-entity-utility';
import { rowId } from '../base-entity-embedded/embedded-aggregate';
import { EmbeddedBreadcrumbLevel } from '../base-entity-embedded/embedded-route-context';
import { BaseEntityDescriptorRegistry } from '../base-entity-facade/base-entity-descriptor.registry';
import { BaseEntityStoreApi } from '../base-entity-store/base-entity.store';
import { BaseFormNavigatorSingletonStore } from '../base-form-navigator/base-form-navigator.store';
import { BaseUrlSegments } from '../base-form-navigator/base-url-segments';
import { translateLabel } from '../i18n/entity-label.pipe';
import { RuleViolationsSingletonStore } from '../rule-engine/rule-violations.store';

/** One step of the hierarchy as it is rendered: what to show, and what it is. */
interface EntityCrumb {
  entityName: string;
  /** The crumb's text — the row's identifying value, or what stands in for it. */
  title: string;
  /** The entity's translated name, shown as the crumb's tooltip. */
  label: string;
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
  imports: [CommonModule, MatToolbar, MatIcon, TranslocoPipe],
  template: `
    <mat-toolbar *ngIf="isVisible()">
      <nav class="breadcrumb" [attr.aria-label]="'base_entity.statusbar.breadcrumb_label' | transloco">
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
  isVisible: Signal<boolean> = computed(() => this.crumbs().length > 1 || (this.store != null && (this.store.currentEntity() !== undefined || this.store.selectedEntities().length === 1)));

  private readonly descriptorRegistry = inject(BaseEntityDescriptorRegistry);
  private readonly formNavigator = inject(BaseFormNavigatorSingletonStore);
  private readonly transloco = inject(TranslocoService);
  private readonly violationsStore = inject(RuleViolationsSingletonStore);
  errorCount = this.violationsStore.errorCount;
  warningCount = this.violationsStore.warningCount;
  infoCount = this.violationsStore.infoCount;
  /** The violations of the form the user is actually on, which at depth is the deepest crumb's. */
  showViolationSummary: Signal<boolean> = computed(() => this.violationsStore.entityName() === (this.crumbs().at(-1)?.entityName ?? this.entityDescriptor().entityName) && this.violationsStore.hasViolations());

  // region Angular lifecycle hooks
  ngOnInit(): void {
    this.store = this.entityDescriptor().store as BaseEntityStoreApi<BaseEntity>;
  }

  // endregion

  // region event handling methods
  async onNavigateToCrumb(index: number): Promise<void> {
    await this.formNavigator.navigateToBreadcrumbLevel(index);
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
    if (value != null && String(value).length > 0) return String(value);

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
    return value != null ? String(value) : '';
  }

  private evaluateEntityTitle(title: string | (() => string)): string {
    return typeof title === 'function' ? title() : title;
  }

  // endregion
}
