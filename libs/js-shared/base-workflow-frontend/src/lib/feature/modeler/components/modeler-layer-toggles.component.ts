import { Component, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/** Which layers of a workflow diagram are on. Every one defaults to on; a toggle takes something away. */
export interface ModelerLayers {
  lanes: boolean;
  data: boolean;
  tools: boolean;
}

/** The three, in the order they are offered — outermost structure first, detail last. */
const LAYERS: (keyof ModelerLayers)[] = ['lanes', 'data', 'tools'];

/**
 * What a full workflow diagram lets a reader take away: the lanes, the data and the tools.
 *
 * Beside the legend rather than instead of it, and the reason it exists at all: a workflow of any size draws
 * every task, every artifact each task reads and writes, and every tool its steps call, and that is more than
 * one screen can say at once. The choice is *which question the diagram answers* — turn off data and tools
 * and it is the flow; turn off lanes and it is the flow without regard to who performs it.
 *
 * The flags go to the converter, not to this component's own rendering and not to the canvas. A layer turned
 * off is a layer whose nodes and edges are never built, so the layout re-ranks what remains — where hiding
 * them at the canvas would leave the columns the hidden nodes used to occupy.
 *
 * Plain checkboxes rather than `mat-checkbox`: this library declares no `@angular/material` peer dependency,
 * the same reason `WorkflowElementNodeComponent` draws its symbol with `<img>` rather than `mat-icon` and
 * base-state's modeler saves with a bare `<button>`.
 */
@Component({
  selector: 'pp-modeler-layer-toggles',
  standalone: true,
  imports: [TranslocoPipe],
  template: `
    @for (layer of layerNames; track layer) {
      <label class="toggle" [attr.data-testid]="'modeler-toggle-' + layer">
        <input type="checkbox" [checked]="layers()[layer]" (change)="toggle(layer)" />
        {{ labelScope() + '.' + layer | transloco }}
      </label>
    }
  `,
  styles: `
    :host {
      display: flex;
      align-items: center;
      gap: 16px;
      font-size: 12px;
      color: #666666;
    }
    .toggle {
      display: flex;
      align-items: center;
      gap: 6px;
      cursor: pointer;
      user-select: none;
    }
  `,
})
export class ModelerLayerTogglesComponent {
  readonly layers = input.required<ModelerLayers>();

  /**
   * Transloco key root the three labels hang under — `<scope>.<layer>`, so the host owns the wording.
   *
   * An input rather than a constant of this component, because the labels belong to the screen that offers
   * them: `base-workflow.i18n.spec.ts` asserts that every top-level block of the bundle is an entity scope,
   * so a `base_workflow.modeler.*` block of this component's own would fail it.
   */
  readonly labelScope = input.required<string>();

  /** Emitted with the whole set, so the host holds one signal rather than three. */
  readonly layersChange = output<ModelerLayers>();

  protected readonly layerNames = LAYERS;

  protected toggle(layer: keyof ModelerLayers): void {
    this.layersChange.emit({ ...this.layers(), [layer]: !this.layers()[layer] });
  }
}
