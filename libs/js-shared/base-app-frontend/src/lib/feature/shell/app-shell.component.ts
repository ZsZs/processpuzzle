import { NgComponentOutlet } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { RouterOutlet } from '@angular/router';
import { AppDefinition } from '../../domain/app-definition';
import { AppRegionRenderer, RegionView } from './app-region.renderer';
import { layoutOf, themeVarsOf } from './app-shell.model';
import { NavOrientation } from './region-nav.component';

/**
 * The run-time shell of a metadata-defined application: the chrome an `AppDefinition` describes, with a
 * `<router-outlet>` where its routes render.
 *
 * **This is the application, not a picture of one.** `AppPreviewComponent` hosts it so that the Preview
 * tab shows the real thing, and the standalone runtime host will mount the same component — which is
 * the whole point of it being a component with one input rather than something assembled inside the
 * preview. A shell built for the preview would drift from the one built for production, and a preview
 * that drifts is worse than none.
 *
 * Everything is bound declaratively. Regions are resolved to {@link RegionView}s and rendered through
 * `NgComponentOutlet`, never created imperatively, so that a definition edited in the neighbouring
 * details form re-renders through ordinary signal propagation instead of a clear-and-rebuild that would
 * drop scroll position and focus on every keystroke.
 *
 * Not yet handled, each deliberately: **no routes are registered** under the outlet — that is the
 * nested router context, which needs `EntityTabDescriptor` to be able to carry child routes; and
 * `materialTheme` / `colorScheme` are not applied, because a Material theme wants `:root` and does not
 * scope to a subtree. Only the `--pp-*` overrides are, and those cascade.
 */
@Component({
  selector: 'pp-app-shell',
  standalone: true,
  imports: [NgComponentOutlet, MatSidenav, MatSidenavContainer, MatSidenavContent, RouterOutlet],
  // Theme custom properties go on the host rather than on a wrapper, so the element that *is* the shell
  // is also the element they cascade from — and so the host can be the grid, see the styles below.
  host: { '[style]': 'themeVars()' },
  template: `
    @if (headerView() || topNavView()) {
      <div class="pp-app-shell__top">
        @if (headerView(); as view) {
          <ng-container *ngComponentOutlet="view.component; inputs: view.inputs"></ng-container>
        }
        @if (topNavView(); as view) {
          <ng-container *ngComponentOutlet="view.component; inputs: topNavInputs()"></ng-container>
        }
      </div>
    }

    <mat-sidenav-container class="pp-app-shell__body">
      @if (sidenavView(); as view) {
        <mat-sidenav class="pp-app-shell__sidenav" [mode]="layout().sidenavMode" [opened]="layout().sidenavOpened" [position]="layout().sidenavPosition">
          <ng-container *ngComponentOutlet="view.component; inputs: sidenavInputs()"></ng-container>
        </mat-sidenav>
      }
      <mat-sidenav-content>
        <main class="pp-app-shell__content" [style.max-width]="layout().contentMaxWidth">
          <router-outlet />
        </main>
      </mat-sidenav-content>
    </mat-sidenav-container>

    @if (footerView(); as view) {
      <ng-container *ngComponentOutlet="view.component; inputs: view.inputs"></ng-container>
    }
  `,
  styles: [
    `
      /*
       * The host itself is the three-row grid — header, content, footer — rather than a wrapper inside it.
       * That is what lets a *caller* decide how tall the shell is: a height or a min-height set on
       * pp-app-shell from outside sizes the grid directly, and the 1fr row absorbs the difference, so the
       * footer sits at the bottom of whatever space the host is given. Through a wrapper it could not: a
       * percentage height inside a parent that has only a min-height resolves to auto, the 1fr row
       * collapses to its content, and the footer rides up under the header.
       *
       * height: 100% covers the other case — an ancestor chain that does have definite heights, as the
       * standalone runtime host will.
       */
      :host {
        display: grid;
        grid-template-rows: auto 1fr auto;
        height: 100%;
      }
      .pp-app-shell__top {
        align-items: stretch;
        display: flex;
        background-color: var(--pp-surface-header);
      }
      /* min-height: 0 keeps the sidenav container from being sized by its content and overflowing the
         1fr row — a grid item's automatic minimum size is its content, not zero. */
      .pp-app-shell__body {
        height: 100%;
        min-height: 0;
      }
      .pp-app-shell__sidenav {
        background-color: var(--pp-surface-sidenav);
        color: var(--pp-on-sidenav);
        min-width: 200px;
      }
      .pp-app-shell__content {
        padding: 16px 20px 24px;
      }
    `,
  ],
})
export class AppShellComponent {
  /**
   * The definition to render. Optional rather than required because the shell is mounted before the
   * definition resolves — the preview's store selects the entity in its own `ngOnInit`, and a deep link
   * arrives with nothing loaded — so an empty shell is a state to render, not an error.
   */
  readonly definition = input<AppDefinition | undefined>(undefined);

  private readonly regionRenderer = inject(AppRegionRenderer);

  protected readonly layout = computed(() => layoutOf(this.definition()));
  protected readonly themeVars = computed(() => themeVarsOf(this.definition()));

  private readonly regionViews = computed(() =>
    (this.definition()?.regions ?? []).map((region) => this.regionRenderer.render(region, this.definition())).filter((view): view is RegionView => view !== undefined),
  );

  protected readonly headerView = computed(() => this.viewOf('header'));
  protected readonly footerView = computed(() => this.viewOf('footer'));

  /**
   * The nav in the sidenav, for the two sidenav presets. Split from {@link topNavView} rather than
   * decided in the template, so each slot is one unambiguous `@if` and the mutual exclusion is stated
   * once, here.
   */
  protected readonly sidenavView = computed(() => (this.layout().hasSidenav ? this.viewOf('sidenav') : undefined));

  /**
   * The same nav relocated into the header row under `top-nav`. It is placed beside the header region
   * rather than inside it, so that a `top-nav` app which declares no header region is still navigable —
   * and so that the shell still never invents a region that was not authored.
   */
  protected readonly topNavView = computed(() => (this.layout().hasSidenav ? undefined : this.viewOf('sidenav')));

  /**
   * The nav view's own inputs with the axis the layout dictates laid over them.
   *
   * Computeds rather than a method called from the template: a method would hand `NgComponentOutlet` a
   * freshly built object on every change-detection pass, so it would re-diff and re-apply the nav's
   * inputs continuously. Memoized, the identity only changes when the definition or the preset does.
   */
  protected readonly sidenavInputs = computed(() => this.navInputsOf(this.sidenavView(), 'vertical'));
  protected readonly topNavInputs = computed(() => this.navInputsOf(this.topNavView(), 'horizontal'));

  private navInputsOf(view: RegionView | undefined, orientation: NavOrientation): Record<string, unknown> {
    return { ...view?.inputs, orientation };
  }

  private viewOf(slot: RegionView['slot']): RegionView | undefined {
    // First occurrence wins. An app declares each slot at most once — `type` is the region's identity,
    // see APP_REGION_ID_FIELD — so a second one is a definition that bypassed validation, and ignoring
    // it is the harmless read. The same rule `buildAppRoutes` applies to a duplicate path.
    return this.regionViews().find((view) => view.slot === slot);
  }
}
