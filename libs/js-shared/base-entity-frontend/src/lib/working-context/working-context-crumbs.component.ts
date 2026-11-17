import { Component, computed, inject, Signal } from '@angular/core';
import { MatDivider } from '@angular/material/divider';
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
import { EntityStateSnapshot, EntityStateTransition } from './entity-state-actions';
import { ActiveWorkingContext, WorkingContextService } from './working-context.service';

/** An active working context as it is rendered: one crumb, with its badge and its extra text. */
interface ContextCrumb {
  context: ActiveWorkingContext;
  title: string;
  /** The entity's translated name, shown as the crumb's tooltip. */
  label: string;
  badge: string | undefined;
  extra: string | undefined;
  /** What the entity can do next, from its state machine; empty when none governs it. */
  transitions: EntityStateTransition[];
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
  imports: [MatDivider, MatIcon, MatMenu, MatMenuItem, MatMenuTrigger, TranslocoPipe],
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
          [title]="'base_entity.working_context.menu' | transloco"
          [matMenuTriggerFor]="contextMenu"
        >
          <mat-icon>arrow_drop_down</mat-icon>
        </button>
        <mat-menu #contextMenu="matMenu">
          @for (transition of crumb.transitions; track transition.key) {
            <button
              mat-menu-item
              [attr.data-testid]="crumb.testId + '-menu-transition-' + transition.key"
              [disabled]="!transition.enabled || firing()"
              [title]="transition.blockedReason ?? ''"
              (click)="onFire(crumb, transition)"
            >
              <mat-icon>play_arrow</mat-icon><span>{{ transition.label }}</span>
            </button>
          }
          @if (crumb.transitions.length > 0) {
            <mat-divider />
          }
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
      /* A visible control, not a bare glyph: a bordered chip that darkens on hover and shows keyboard focus. */
      .context-menu-trigger {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        align-self: center;
        width: 28px;
        min-height: 28px;
        height: 28px;
        padding: 0;
        border: 1px solid currentColor;
        border-radius: 50%;
        background-color: rgba(0, 0, 0, 0.08);
      }
      .context-menu-trigger:hover,
      .context-menu-trigger[aria-expanded='true'] {
        background-color: rgba(0, 0, 0, 0.2);
      }
      .context-menu-trigger:focus-visible {
        outline: 2px solid currentColor;
        outline-offset: 2px;
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
  protected readonly firing = this.workingContext.firing;
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

  protected async onFire(crumb: ContextCrumb, transition: EntityStateTransition): Promise<void> {
    await this.workingContext.fire(crumb.context.name, transition.key);
  }

  protected async onClear(crumb: ContextCrumb): Promise<void> {
    await this.workingContext.clear(crumb.context.name);
  }

  /** `badge: state` shows the state's name — "Running", not the key a machine stores — when the machine is known. */
  private badgeOf(context: ActiveWorkingContext, snapshot: EntityStateSnapshot | undefined, stateAttr: string | undefined): unknown {
    const badge = context.definition.display?.badge;
    if (!badge) return undefined;
    if (badge === 'state' && snapshot) return snapshot.currentLabel;
    return valueAt(context.entity, badge, context.descriptor, stateAttr);
  }

  private crumbOf(context: ActiveWorkingContext): ContextCrumb {
    const { definition, descriptor, entity, id } = context;
    const stateAttr = this.workingContext.stateAttributeOf(context);
    const snapshot = this.workingContext.stateOf(context.name);
    const badgeValue = this.badgeOf(context, snapshot, stateAttr);
    const extra = definition.display?.extra && entity ? renderTemplate(definition.display.extra, entity, descriptor, stateAttr) : undefined;
    return {
      context,
      title: contextLabel(definition.display?.label, entity, descriptor, id),
      label: descriptor ? translateLabel(this.transloco, descriptor.i18nKey(), descriptor.entityName) : definition.entityName,
      badge: badgeValue == null || badgeValue === '' ? undefined : String(badgeValue),
      extra: extra && extra.length > 0 ? extra : undefined,
      transitions: snapshot?.transitions ?? [],
      testId: createTestId(definition.entityName, 'context-crumb'),
    };
  }
}
