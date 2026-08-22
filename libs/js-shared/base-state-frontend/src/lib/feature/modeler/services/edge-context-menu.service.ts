import { Injectable, signal } from '@angular/core';

/** Which edge was right-clicked, and where the pointer was when it happened — in viewport coordinates. */
export interface EdgeContextMenuTarget {
  edgeId: string;
  clientX: number;
  clientY: number;
}

/**
 * The channel from a right-click on an edge to the menu that answers it.
 *
 * A service and not an output, because the component that sees the click — `TransitionEdgeComponent` — is
 * instantiated by ng-diagram from the edge template map, not by a template of ours, so there is no binding
 * to hang an output on. Same reason `DiagramSelectionService` exists.
 *
 * Provided by {@link StateMachineCanvasComponent} rather than in the root injector, unlike that one: this
 * is one canvas's transient interaction state, and two canvases on a screen sharing a single open menu
 * would be a bug rather than a feature.
 *
 * Viewport coordinates are stored as they arrive from the `MouseEvent`. Turning them into a position
 * inside the canvas is the canvas's job — it is the one that knows where it sits.
 */
@Injectable()
export class EdgeContextMenuService {
  private readonly targetSignal = signal<EdgeContextMenuTarget | undefined>(undefined);

  /** The open menu's subject, or `undefined` when no menu is open. */
  readonly target = this.targetSignal.asReadonly();

  open(edgeId: string, { clientX, clientY }: MouseEvent): void {
    this.targetSignal.set({ edgeId, clientX, clientY });
  }

  close(): void {
    this.targetSignal.set(undefined);
  }
}
