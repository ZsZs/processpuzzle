import { Component, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { STATE_MACHINE_DEFINITION_I18N_SCOPE } from '../../../base-state.i18n';
import { EDGE_ROUTING_CHOICES, EdgeRoutingChoice } from './edge-routing-options';

/**
 * The menu a right-click on a transition opens: how that transition should be routed.
 *
 * Deliberately dumb — it is told where to appear and which routing is in force, and reports what was
 * picked. It touches neither the diagram model nor {@link EdgeContextMenuService}, so which edge is being
 * routed is none of its business and {@link StateMachineCanvasComponent} stays the only writer of the model.
 *
 * Hand-rolled rather than a `mat-menu`, because this library declares no `@angular/material` peer
 * dependency and one context menu is not the reason to acquire one — the same call the modeler's Save
 * button makes.
 *
 * Positioned absolutely within the canvas, which is why the canvas is the one that renders it: `x` and `y`
 * are offsets inside that box, not viewport coordinates, so the menu survives a scrolled page and does not
 * depend on whether an ancestor happens to be transformed.
 */
@Component({
  selector: 'pp-edge-routing-menu',
  standalone: true,
  imports: [TranslocoPipe],
  host: {
    // The two ways out that cost the user nothing: click elsewhere, or press Escape. Bound on the document
    // because the whole point is to catch what happens *outside* this element. A right-click elsewhere
    // closes it too — on another edge it is reopened by that edge, which is the same gesture retargeted.
    '(document:click)': 'closed.emit()',
    '(document:contextmenu)': 'closed.emit()',
    '(document:keydown.escape)': 'closed.emit()',
  },
  template: `
    <div class="pp-routing-menu" role="menu" [style.left.px]="x()" [style.top.px]="y()" data-testid="edge-routing-menu">
      <div class="pp-routing-menu__title">{{ scope + '.modeler.routing.title' | transloco }}</div>
      @for (choice of choices; track choice) {
        <button
          type="button"
          role="menuitemradio"
          class="pp-routing-menu__item"
          [class.pp-routing-menu__item--active]="choice === active()"
          [attr.aria-checked]="choice === active()"
          [attr.data-testid]="'routing-' + choice"
          (click)="chosen.emit(choice)"
        >
          <span class="pp-routing-menu__tick" aria-hidden="true">{{ choice === active() ? '✓' : '' }}</span>
          {{ scope + '.modeler.routing.' + choice | transloco }}
        </button>
      }
    </div>
  `,
  styles: `
    .pp-routing-menu {
      position: absolute;
      z-index: 10;
      min-width: 160px;
      padding: 4px 0;
      background: #ffffff;
      border: 1px solid #cccccc;
      border-radius: 4px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
      font-size: 14px;
      user-select: none;
    }
    .pp-routing-menu__title {
      padding: 4px 12px 6px;
      font-size: 12px;
      text-transform: uppercase;
      color: #666666;
    }
    .pp-routing-menu__item {
      display: flex;
      align-items: center;
      gap: 8px;
      width: 100%;
      padding: 6px 12px;
      border: none;
      background: none;
      font: inherit;
      text-align: left;
      cursor: pointer;
    }
    .pp-routing-menu__item:hover {
      background-color: #f0f0f0;
    }
    .pp-routing-menu__item--active {
      font-weight: 600;
    }
    /* Reserves the tick's width on every row, so the labels line up whichever one is ticked. */
    .pp-routing-menu__tick {
      width: 12px;
      color: var(--pp-color-dark-blue, rgb(24, 111, 206));
    }
  `,
})
export class EdgeRoutingMenuComponent {
  /** Where to appear, as an offset inside the canvas. */
  readonly x = input.required<number>();
  readonly y = input.required<number>();
  /** The routing the edge is drawn with now, which is the item shown as ticked. */
  readonly active = input.required<EdgeRoutingChoice>();

  readonly chosen = output<EdgeRoutingChoice>();
  readonly closed = output<void>();

  protected readonly choices = EDGE_ROUTING_CHOICES;
  protected readonly scope = STATE_MACHINE_DEFINITION_I18N_SCOPE;
}
