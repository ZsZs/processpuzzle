import { Component, computed, inject, Signal } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { createTestId } from '../base-entity/base-entity-utility';
import { EntityRouteRegistry } from '../base-form-navigator/entity-route.registry';
import { translateLabel } from '../i18n/entity-label.pipe';
import { contextLabel, renderTemplate, valueAt } from './working-context-attributes';
import { WORKING_CONTEXT_DRAWER_CONFIG, WorkingContextPickerDialog, WorkingContextPickerDialogData } from './working-context-picker.dialog';
import { ActiveWorkingContext, WorkingContextService } from './working-context.service';

/** An active working context as it is rendered: one crumb, with its badge and its extra text. */
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
 * The active working contexts, outermost first, as crumbs: the label, a badge, an extra text, and a menu to
 * switch (the picker as a drawer), open the entity for editing, or clear the context.
 *
 * It is the root of the status bar's breadcrumb — `BaseEntityStatusbarComponent` renders it before the entity
 * levels — and, in an application shell with a `statusbar` region, the whole content of that region, so the
 * context is visible on pages that have no entity form at all.
 */
@Component({
  selector: 'pp-working-context-crumbs',
  standalone: true,
  imports: [MatIcon, MatMenu, MatMenuItem, MatMenuTrigger, TranslocoPipe],
  template: `
    @for (crumb of crumbs(); track crumb.context.name; let isLast = $last) {
      <span class="context-crumb" [attr.data-testid]="crumb.testId">
        <button type="button" class="crumb-context" [attr.data-testid]="crumb.testId + '-switch'" [title]="crumb.label" (click)="onSwitch(crumb)">
          {{ crumb.title }}
          @if (crumb.extra) {
            <span class="context-extra">{{ crumb.extra }}</span>
          }
        </button>
        @if (crumb.badge) {
          <span class="context-badge" [attr.data-testid]="crumb.testId + '-badge'">{{ crumb.badge }}</span>
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
          <button mat-menu-item [attr.data-testid]="crumb.testId + '-menu-switch'" (click)="onSwitch(crumb)">
            <mat-icon>swap_horiz</mat-icon><span>{{ 'base_entity.working_context.switch' | transloco }}</span>
          </button>
          <button mat-menu-item [attr.data-testid]="crumb.testId + '-menu-open'" (click)="onOpen(crumb)">
            <mat-icon>edit</mat-icon><span>{{ 'base_entity.working_context.open' | transloco }}</span>
          </button>
          <button mat-menu-item [attr.data-testid]="crumb.testId + '-menu-clear'" (click)="onClear(crumb)">
            <mat-icon>close</mat-icon><span>{{ 'base_entity.working_context.clear' | transloco }}</span>
          </button>
        </mat-menu>
      </span>
      @if (!isLast) {
        <mat-icon class="context-separator">chevron_right</mat-icon>
      }
    }
  `,
  styles: [
    `
      :host {
        display: inline-flex;
        align-items: center;
        min-width: 0;
        gap: 2px;
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
      .crumb-context {
        overflow: hidden;
        max-width: 24ch;
        text-overflow: ellipsis;
        white-space: nowrap;
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
      .context-badge {
        padding: 2px 8px;
        border-radius: 12px;
        background-color: rgba(0, 0, 0, 0.08);
        font-size: 13px;
        line-height: 1;
      }
      .context-separator {
        width: 18px;
        height: 18px;
        font-size: 18px;
        line-height: 18px;
        opacity: 0.6;
      }
    `,
  ],
})
export class WorkingContextCrumbsComponent {
  readonly crumbs: Signal<ContextCrumb[]> = computed(() => this.workingContext.active().map((context) => this.crumbOf(context)));

  private readonly workingContext = inject(WorkingContextService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly entityRouteRegistry = inject(EntityRouteRegistry);
  private readonly transloco = inject(TranslocoService);

  /** Opens the context's picker as a drawer; picking in it switches, and closes it. */
  protected onSwitch(crumb: ContextCrumb): void {
    const data: WorkingContextPickerDialogData = { contextName: crumb.context.name, entityLabel: crumb.label };
    this.dialog.open(WorkingContextPickerDialog, { ...WORKING_CONTEXT_DRAWER_CONFIG, data });
  }

  /** The context's entity in its own form, for editing it — the context stays active. */
  protected async onOpen(crumb: ContextCrumb): Promise<void> {
    const url = this.entityRouteRegistry.detailsPath(crumb.context.definition.entityName, crumb.context.id);
    if (url) await this.router.navigateByUrl(url);
  }

  protected async onClear(crumb: ContextCrumb): Promise<void> {
    await this.workingContext.clear(crumb.context.name);
  }

  private crumbOf(context: ActiveWorkingContext): ContextCrumb {
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
}
