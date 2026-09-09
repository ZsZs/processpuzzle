import { Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { NgDiagramPaletteItemComponent, NgDiagramPaletteItemPreviewComponent } from 'ng-diagram';
import { STATE_MACHINE_DEFINITION_I18N_SCOPE } from '../../../base-state.i18n';
import { STATE_PALETTE_ITEMS } from './state-palette-items';

/**
 * The symbols a machine is drawn from, draggable onto the canvas: an entry point, an exit, and a plain
 * state. What each one *means* is {@link STATE_PALETTE_ITEMS}' business — this component only draws them.
 *
 * Each symbol is its UML shape — filled disc, ringed disc, rounded box — with its name written next to it.
 * Next to rather than in, and this is the one place the two differ: on the canvas a disc's name belongs to
 * a state and is drawn under the shape, whereas here it names the *tool*, so it reads as a labelled row.
 * The shapes themselves are {@link StateNodeComponent}'s, which is where the originals of these styles are.
 *
 * Rendered from inside {@link StateMachineCanvasComponent} rather than as a sibling column in the modeler
 * tab, and not by choice: `<ng-diagram-palette-item>` hands the dragged item to ng-diagram's own
 * `PaletteService`, which `provideNgDiagram()` supplies at *component* scope. A palette outside the
 * component that provides it would resolve a second, unrelated service and drag nothing — the same
 * arrangement ng-diagram's own palette guide shows.
 *
 * Each item is drawn twice: once in the palette and once inside `<ng-diagram-palette-item-preview>`, which
 * is what follows the cursor during the drag. The same markup for both, so what is picked up looks like
 * what was grabbed. `StateNodeComponent` is deliberately *not* reused: it draws a node from a `Node`, and
 * a palette entry has no node behind it yet — only the placeholder in its `data`.
 */
@Component({
  selector: 'pp-element-palette',
  standalone: true,
  imports: [TranslocoPipe, NgDiagramPaletteItemComponent, NgDiagramPaletteItemPreviewComponent],
  template: `
    <h4>{{ scope + '.modeler.palette.title' | transloco }}</h4>

    @for (item of items; track item.data.kind) {
      <ng-diagram-palette-item [item]="item">
        <div class="pp-palette__symbol" [class]="'pp-palette__symbol--' + item.data.kind" [attr.data-testid]="'palette-' + item.data.kind">
          <span class="pp-palette__glyph" aria-hidden="true"></span>
          {{ scope + '.modeler.palette.' + item.data.kind | transloco }}
        </div>

        <ng-diagram-palette-item-preview>
          <div class="pp-palette__symbol" [class]="'pp-palette__symbol--' + item.data.kind">
            <span class="pp-palette__glyph" aria-hidden="true"></span>
            {{ scope + '.modeler.palette.' + item.data.kind | transloco }}
          </div>
        </ng-diagram-palette-item-preview>
      </ng-diagram-palette-item>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding: 8px;
      width: 150px;
      flex: none;
      border-right: 1px solid #cccccc;
      background-color: #fafafa;
      user-select: none;
    }
    h4 {
      margin: 0;
      font-size: 12px;
      text-transform: uppercase;
      color: #666666;
    }
    /* A row: the shape, then what it is called. */
    .pp-palette__symbol {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 6px 8px;
      border-radius: 6px;
      background: #ffffff;
      border: 1px solid #cccccc;
      box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
      font-weight: 600;
      cursor: grab;
    }
    /* Scaled-down copies of the three canvas shapes. Fixed-size and non-shrinking, so a long translation
       squeezes the label and never the symbol — the symbol is what is being pointed at. */
    .pp-palette__glyph {
      box-sizing: border-box;
      position: relative;
      flex: none;
    }
    .pp-palette__symbol--start .pp-palette__glyph {
      width: 16px;
      height: 16px;
      border-radius: 50%;
      background: var(--pp-color-dark-blue, rgb(24, 111, 206));
    }
    .pp-palette__symbol--end .pp-palette__glyph {
      width: 20px;
      height: 20px;
      border-radius: 50%;
      background: #ffffff;
      border: 2px solid #d9534f;
    }
    .pp-palette__symbol--end .pp-palette__glyph::after {
      content: '';
      position: absolute;
      inset: 3px;
      border-radius: 50%;
      background: #d9534f;
    }
    .pp-palette__symbol--state .pp-palette__glyph {
      width: 22px;
      height: 14px;
      border-radius: 3px;
      background: #ffffff;
      border: 1px solid #cccccc;
    }
  `,
})
export class ElementPaletteComponent {
  protected readonly items = STATE_PALETTE_ITEMS;
  protected readonly scope = STATE_MACHINE_DEFINITION_I18N_SCOPE;
}
