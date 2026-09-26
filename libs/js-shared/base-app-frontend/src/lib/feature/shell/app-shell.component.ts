import { Dialog } from '@angular/cdk/dialog';
import { OverlayContainer } from '@angular/cdk/overlay';
import { NgComponentOutlet } from '@angular/common';
import { Component, computed, effect, inject, input } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { RouterOutlet } from '@angular/router';
import { SidenavAutosizeDirective, ThemeService } from '@processpuzzle/widgets';
import { AppDefinition } from '../../domain/app-definition';
import { AppRegionRenderer, RegionView } from './app-region.renderer';
import { layoutOf, themeDefaultsOf, themeVarsOf } from './app-shell.model';
import { NavOrientation } from './region-nav.component';
import { ShellOverlayContainer } from './shell-overlay-container';

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
 * The whole theme is applied here, not only the brand colours: `--pp-*` overrides as a style binding, and
 * a class selecting one of the scoped Material themes of widgets' `src/theme/pp-material-themes.scss`. The
 * class comes from the shell's own {@link ThemeService}: the definition's `materialTheme` / `colorScheme`
 * are its defaults, and a `ThemesButton` placed in one of the regions overrides them for this application
 * only. Both work because a Material theme is a set of custom properties and nothing else. That file is a
 * **global stylesheet the application registers**; if it is not registered the classes still land and the
 * shell simply inherits the host's theme. Overlays opened from inside the shell render into a
 * {@link ShellOverlayContainer} in its host, so they are themed as well.
 *
 * Not yet handled, deliberately: **no routes are registered** under the outlet. That is the nested router
 * context, which needs `EntityTabDescriptor` to be able to carry child routes.
 */
@Component({
  selector: 'pp-app-shell',
  standalone: true,
  imports: [NgComponentOutlet, MatSidenav, MatSidenavContainer, MatSidenavContent, RouterOutlet, SidenavAutosizeDirective],
  providers: [ThemeService, { provide: OverlayContainer, useClass: ShellOverlayContainer }, Dialog, MatDialog],
  // Both theme bindings go on the host rather than on a wrapper, so the element that *is* the shell is
  // also the element they cascade from — and so the host can be the grid, see the stylesheet. `[class]`
  // selects one of the scoped Material themes there; `[style]` carries the `--pp-*` overrides.
  host: { '[class]': 'themeClass()', '[style]': 'themeVars()' },
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
        <mat-sidenav class="pp-app-shell__sidenav" ppSidenavAutosize [mode]="layout().sidenavMode" [opened]="layout().sidenavOpened" [position]="layout().sidenavPosition">
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
  styleUrl: 'app-shell.component.scss',
})
export class AppShellComponent {
  /**
   * The definition to render. Optional rather than required because the shell is mounted before the
   * definition resolves — the preview's store selects the entity in its own `ngOnInit`, and a deep link
   * arrives with nothing loaded — so an empty shell is a state to render, not an error.
   */
  readonly definition = input<AppDefinition | undefined>(undefined);

  /**
   * Whether a theme the user picks in a `ThemesButton` inside the shell is remembered, per application id.
   * The designer's preview turns it off: there a choice is only tried out, and a stored one would shadow the
   * theme being edited in the neighbouring form.
   */
  readonly persistTheme = input(true);

  private readonly regionRenderer = inject(AppRegionRenderer);
  private readonly theme = inject(ThemeService);

  protected readonly layout = computed(() => layoutOf(this.definition()));
  protected readonly themeVars = computed(() => themeVarsOf(this.definition()));
  protected readonly themeClass = this.theme.themeClass;

  // Re-run on every edit of the definition. Persisting, that re-reads the same stored choice; in the preview
  // it drops the choice tried out there, so an edit to the theme fields always shows.
  private readonly applyThemeDefaults = effect(() => {
    const definition = this.definition();
    this.theme.setDefaults(themeDefaultsOf(definition));
    this.theme.persistUnder(this.persistTheme() && definition?.id ? `pp-theme:${definition.id}` : null);
  });

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
