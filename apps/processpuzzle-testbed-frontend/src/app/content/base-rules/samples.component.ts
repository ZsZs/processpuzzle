import { Component, computed, inject, Signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatDivider } from '@angular/material/divider';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';
import { ORDER_NAME, ORDER_PATH, SPECIAL_ORDER_NAME, SPECIAL_ORDER_PATH } from './rule-sample.routes';

/**
 * Samples tab of the Base Rule section: the two entities the seeded sample rules are written against, each
 * rendered by the framework's generated screens with the rule engine active.
 *
 * The point of the pair is the contrast. `Order` and `Special Order` are near-identical definitions; what
 * differs is that `special-order-allows-zero-quantity` overrides `positive-quantities` for the second. Open
 * SPO-2001, whose second line is a zero-quantity placeholder, and the form is clean; put the same line on an
 * `Order` and it is refused. That is the extends/override convention doing something visible.
 *
 * The toggle's selection is derived from the URL rather than set on click, so a deep link and a browser Back
 * both leave the right button pressed — the state lives in one place, the router.
 */
@Component({
  selector: 'base-rules-samples',
  standalone: true,
  imports: [RouterLink, RouterOutlet, MatButtonToggleGroup, MatButtonToggle, MatDivider, TranslocoDirective],
  template: `
    <ng-container *transloco="let t; prefix: 'base-rules'">
      <div style="margin-bottom: 20px">{{ t('samples_desc_1') }}</div>
      <div>
        <strong>{{ t('samples_desc_2') }}</strong>
      </div>
      <div style="margin-top: 8px">
        <!-- Nothing on this tab edits a rule. Authoring is the designer's own screen, reached absolutely
             because the Design section is a sibling of this one, not a child. -->
        {{ t('samples_edit_hint') }} <a routerLink="/design/base-rule/list">{{ t('samples_edit_link') }}</a>
      </div>
      <div style="margin-top: 20px">
        <mat-button-toggle-group name="ruleSample" [value]="selectedSample()" aria-label="Rule Sample">
          <mat-button-toggle [routerLink]="orderPath" [value]="orderPath">{{ orderName }}</mat-button-toggle>
          <mat-button-toggle [routerLink]="specialOrderPath" [value]="specialOrderPath">{{ specialOrderName }}</mat-button-toggle>
        </mat-button-toggle-group>
      </div>
      <mat-divider />
      <router-outlet></router-outlet>
    </ng-container>
  `,
})
export class SamplesComponent {
  protected readonly orderName = ORDER_NAME;
  protected readonly orderPath = ORDER_PATH;
  protected readonly specialOrderName = SPECIAL_ORDER_NAME;
  protected readonly specialOrderPath = SPECIAL_ORDER_PATH;

  private readonly router = inject(Router);
  private readonly url: Signal<string>;

  /**
   * Which button is pressed, read off the URL.
   *
   * Whole segments, not `includes()` on the URL string: `special-order` ends in `order`, so a substring test
   * would light the `Order` button on every Special Order screen.
   */
  readonly selectedSample: Signal<string>;

  constructor() {
    this.url = toSignal(
      this.router.events.pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        map((event) => event.urlAfterRedirects),
      ),
      { initialValue: this.router.url },
    );
    this.selectedSample = computed(() => {
      const segments = this.url().split('/');
      if (segments.includes(SPECIAL_ORDER_PATH)) return SPECIAL_ORDER_PATH;
      if (segments.includes(ORDER_PATH)) return ORDER_PATH;
      return '';
    });
  }
}
